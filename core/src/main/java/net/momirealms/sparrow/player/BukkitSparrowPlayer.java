package net.momirealms.sparrow.player;

import com.mojang.datafixers.util.Pair;
import io.netty.channel.ChannelHandler;
import net.kyori.adventure.text.Component;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.DeathProtection;
import net.momirealms.sparrow.proxy.bukkit.entity.CraftPlayerProxy;
import net.momirealms.sparrow.proxy.bukkit.util.CraftChatMessageProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.AdvancementProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.CriterionProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.DisplayInfoProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.ImpossibleTriggerProxy;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.proxy.minecraft.world.item.ItemStackTemplateProxy;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.VersionHelper;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BukkitSparrowPlayer extends SparrowPlayer {
    private volatile Player platformPlayer; // Join 时绑定, Quit 时释放

    BukkitSparrowPlayer(@NotNull ChannelHandler connection, @NotNull UUID uniqueId, @NotNull String name) {
        super(connection, uniqueId, name);
    }

    void initialize(@NotNull Player player) {
        this.platformPlayer = player;
    }

    void close() {
        this.platformPlayer = null;
    }

    @Nullable
    public Player platformPlayer() {
        return this.platformPlayer;
    }

    @Override
    public boolean initialized() {
        return this.platformPlayer != null;
    }

    @Override
    @NotNull
    @SuppressWarnings("deprecation")
    public Locale locale() {
        return Locale.forLanguageTag(this.requirePlayer().getLocale().replace('_', '-'));
    }

    @Override
    public boolean hasPermission(@NotNull String permission) {
        return this.requirePlayer().hasPermission(permission);
    }

    @Override
    public void sendMessage(@NotNull Component message, boolean overlay) {
        Player player = this.requirePlayer();
        Object component = CraftChatMessageProxy.INSTANCE.fromJSON(AdventureHelper.componentToJson(message));
        Object handle = CraftPlayerProxy.INSTANCE.getHandle(player);
        ServerPlayerProxy.INSTANCE.sendSystemMessage(handle, component, overlay);
    }

    @Override
    public void sendTitle(@NotNull Component title, @NotNull Component subtitle, int fadeIn, int stay, int fadeOut) {
        this.sendPacket(new ClientboundBundlePacket(List.of(
                new ClientboundSetTitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(title))),
                new ClientboundSetSubtitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(subtitle))),
                new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut)
        )));
    }

    @Override
    public void sendTotemAnimation(@NotNull ItemStack totem) {
        Player player = this.requirePlayer();
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        int entityId = player.getEntityId();
        net.minecraft.world.item.ItemStack mainHand = CraftItemStack.asNMSCopy(player.getInventory().getItemInMainHand());
        net.minecraft.world.item.ItemStack offHand = CraftItemStack.asNMSCopy(player.getInventory().getItemInOffHand());
        boolean mainHandTotem = mainHand.has(DataComponents.DEATH_PROTECTION);
        net.minecraft.world.item.ItemStack animation = CraftItemStack.asNMSCopy(totem);
        animation.set(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING);
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>(5);
        if (mainHandTotem) {
            packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY))));
        }
        packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.OFFHAND, animation))));
        packets.add(new ClientboundEntityEventPacket(handle, (byte) 35));
        if (mainHandTotem) {
            packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.MAINHAND, mainHand))));
        }
        packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.OFFHAND, offHand))));
        this.sendPacket(new ClientboundBundlePacket(packets));
    }

    @Override
    public void sendDemo() {
        this.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, 0.0f));
    }

    @Override
    public void sendCredits() {
        this.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 1.0f));
    }

    @Override
    public void sendToast(@NotNull Component text, @NotNull ItemStack icon, @NotNull ToastType type) {
        Identifier id = Identifier.fromNamespaceAndPath("sparrow", "toast");
        net.minecraft.world.item.ItemStack minecraftIcon = CraftItemStack.asNMSCopy(icon);
        net.minecraft.network.chat.Component title = CraftChatMessage.fromJSON(AdventureHelper.componentToJson(text));
        DisplayInfo display;
        if (VersionHelper.isOrAbove26_1()) {
            display = (DisplayInfo) DisplayInfoProxy.INSTANCE.create$0(ItemStackTemplateProxy.INSTANCE.fromNonEmptyStack(minecraftIcon), title, net.minecraft.network.chat.Component.empty(), Optional.empty(), AdvancementType.valueOf(type.name()), true, false, true);
        } else {
            display = (DisplayInfo) DisplayInfoProxy.INSTANCE.create(minecraftIcon, title, net.minecraft.network.chat.Component.empty(), Optional.empty(), AdvancementType.valueOf(type.name()), true, false, true);
        }
        Object criterion = CriterionProxy.INSTANCE.create(ImpossibleTriggerProxy.INSTANCE.create(), ImpossibleTriggerProxy.TriggerInstanceProxy.INSTANCE.create());
        AdvancementRequirements requirements = new AdvancementRequirements(List.of(List.of("impossible")));
        Advancement advancement = (Advancement) AdvancementProxy.INSTANCE.create(Optional.empty(), Optional.of(display), AdvancementRewards.EMPTY, Map.of("impossible", criterion), requirements, false);
        AdvancementProgress progress = new AdvancementProgress();
        progress.update(requirements);
        progress.grantProgress("impossible");
        // 客户端处理完成进度时会保存 Toast, 后续移除临时进度不会移除已排队的提示.
        this.sendPacket(new ClientboundBundlePacket(List.of(
                new ClientboundUpdateAdvancementsPacket(false, List.of(new AdvancementHolder(id, advancement)), Set.of(), Map.of(id, progress), true),
                new ClientboundUpdateAdvancementsPacket(false, List.of(), Set.of(id), Map.of(), true)
        )));
    }

    private Player requirePlayer() {
        Player player = this.platformPlayer;
        if (player == null) {
            throw new IllegalStateException("Player has not joined or has already left: " + this.uniqueId());
        }
        return player;
    }
}
