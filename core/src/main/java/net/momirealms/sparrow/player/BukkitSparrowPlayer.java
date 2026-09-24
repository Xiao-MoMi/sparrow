package net.momirealms.sparrow.player;

import com.mojang.datafixers.util.Pair;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.advancement.AdvancementFrame;
import net.momirealms.sparrow.advancement.ToastPackets;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.DeathProtection;
import net.momirealms.sparrow.proxy.bukkit.entity.CraftPlayerProxy;
import net.momirealms.sparrow.proxy.bukkit.util.CraftChatMessageProxy;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class BukkitSparrowPlayer implements SparrowPlayer {
    private final PlayerConnection connection;
    private final Player platformPlayer;

    BukkitSparrowPlayer(@NotNull PlayerConnection connection, @NotNull Player platformPlayer) {
        this.connection = connection;
        this.platformPlayer = platformPlayer;
    }

    @NotNull
    public Player platformPlayer() {
        return this.platformPlayer;
    }

    @Override
    @NotNull
    public UUID uniqueId() {
        return this.connection.uniqueId();
    }

    @Override
    @NotNull
    public String name() {
        return this.connection.name();
    }

    @Override
    @NotNull
    public PlayerConnection connection() {
        return this.connection;
    }

    @Override
    @NotNull
    @SuppressWarnings("deprecation")
    public Locale locale() {
        return Locale.forLanguageTag(this.platformPlayer.getLocale().replace('_', '-'));
    }

    @Override
    public boolean hasPermission(@NotNull String permission) {
        return this.platformPlayer.hasPermission(permission);
    }

    @Override
    public void sendMessage(@NotNull Component message, boolean overlay) {
        Object component = CraftChatMessageProxy.INSTANCE.fromJSON(AdventureHelper.componentToJson(message));
        Object handle = CraftPlayerProxy.INSTANCE.getHandle(this.platformPlayer);
        ServerPlayerProxy.INSTANCE.sendSystemMessage(handle, component, overlay);
    }

    @Override
    public void sendTitle(@NotNull Component title, @NotNull Component subtitle, int fadeIn, int stay, int fadeOut) {
        this.connection.sendPacket(new ClientboundBundlePacket(List.of(
                new ClientboundSetTitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(title))),
                new ClientboundSetSubtitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(subtitle))),
                new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut)
        )));
    }

    @Override
    public void sendTotemAnimation(@NotNull ItemStack totem) {
        Player player = this.platformPlayer;
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
        this.connection.sendPacket(new ClientboundBundlePacket(packets));
    }

    @Override
    public void sendDemo() {
        this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, 0.0f));
    }

    @Override
    public void sendCredits() {
        this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 1.0f));
    }

    @Override
    public void sendToast(@NotNull Component text, @NotNull ItemStack icon, @NotNull AdvancementFrame frame) {
        this.connection.sendPacket(ToastPackets.create(text, icon, frame));
    }
}
