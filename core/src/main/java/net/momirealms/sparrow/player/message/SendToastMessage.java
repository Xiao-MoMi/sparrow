package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.proxy.minecraft.advancements.AdvancementProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.CriterionProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.DisplayInfoProxy;
import net.momirealms.sparrow.proxy.minecraft.advancements.ImpossibleTriggerProxy;
import net.momirealms.sparrow.proxy.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacketProxy;
import net.momirealms.sparrow.proxy.minecraft.world.item.ItemStackTemplateProxy;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.ItemUtils;
import net.momirealms.sparrow.util.VersionHelper;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SendToastMessage extends AbstractPlayerTextMessage {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "send_toast");
    public static final MessageCodec<FriendlyByteBuf, SendToastMessage> CODEC = RedisMessage.codec(SendToastMessage::write, SendToastMessage::new);

    private final Text text;
    private final CompoundTag icon;
    private final AdvancementType frame;

    public SendToastMessage(@NotNull UUID playerId, @NotNull Component text, @NotNull ItemStack icon, @NotNull AdvancementType frame) {
        super(playerId);
        this.text = new Text(text);
        this.icon = ItemUtils.toNbt(CraftItemStack.unwrap(icon));
        this.frame = frame;
    }

    public SendToastMessage(@NotNull UUID playerId, @NotNull String text, @NotNull ItemStack icon, @NotNull AdvancementType frame) {
        super(playerId);
        this.text = new Text(text);
        this.icon = ItemUtils.toNbt(CraftItemStack.unwrap(icon));
        this.frame = frame;
    }

    private SendToastMessage(@NotNull UUID playerId, @NotNull Text text, @NotNull ItemStack icon, @NotNull AdvancementType frame) {
        super(playerId);
        this.text = text;
        this.icon = ItemUtils.toNbt(CraftItemStack.unwrap(icon));
        this.frame = frame;
    }

    @NotNull
    public static SendToastMessage translated(@NotNull UUID playerId, @NotNull Component text, @NotNull ItemStack icon, @NotNull AdvancementType frame) {
        return new SendToastMessage(playerId, Text.translated(text), icon, frame);
    }

    private SendToastMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.text = new Text(buffer);
        net.momirealms.sparrow.util.FriendlyByteBuf data = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer);
        this.icon = (CompoundTag) data.readNbt(false);
        this.frame = AdvancementType.valueOf(ByteBufHelper.readUtf8(buffer, 64));
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        this.text.write(buffer);
        net.momirealms.sparrow.util.FriendlyByteBuf data = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer);
        data.writeNbt(this.icon, false);
        ByteBufHelper.writeUtf8(buffer, this.frame.name(), 64);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void send(@NotNull BukkitSparrowPlayer player) {
        net.minecraft.world.item.ItemStack minecraftIcon = ItemUtils.fromNbt(this.icon);
        net.minecraft.network.chat.Component title = CraftChatMessage.fromJSON(AdventureHelper.componentToJson(this.text.resolve(player)));
        // 26.1 起进度图标改用物品模板
        DisplayInfo display;
        if (VersionHelper.isOrAbove26_1) {
            display = (DisplayInfo) DisplayInfoProxy.INSTANCE.create$0(
                    ItemStackTemplateProxy.INSTANCE.fromNonEmptyStack(minecraftIcon),
                    title,
                    net.minecraft.network.chat.Component.empty(),
                    Optional.empty(),
                    this.frame,
                    true,
                    false,
                    true
            );
        } else {
            display = (DisplayInfo) DisplayInfoProxy.INSTANCE.create(
                    minecraftIcon,
                    title,
                    net.minecraft.network.chat.Component.empty(),
                    Optional.empty(),
                    this.frame,
                    true,
                    false,
                    true
            );
        }
        Object criterion = CriterionProxy.INSTANCE.create(
                ImpossibleTriggerProxy.INSTANCE.create(),
                ImpossibleTriggerProxy.TriggerInstanceProxy.INSTANCE.create()
        );
        AdvancementRequirements requirements = new AdvancementRequirements(List.of(List.of("impossible")));
        Advancement advancement = (Advancement) AdvancementProxy.INSTANCE.create(
                Optional.empty(),
                Optional.of(display),
                AdvancementRewards.EMPTY,
                Map.of("impossible", criterion),
                requirements,
                false
        );
        AdvancementProgress progress = new AdvancementProgress();
        progress.update(requirements);
        progress.grantProgress("impossible");
        // 客户端处理完成进度时会保存 Toast, 后续移除临时进度不会移除已排队的提示.
        Identifier identifier = Identifier.fromNamespaceAndPath("sparrow", "toast");
        AdvancementHolder holder = new AdvancementHolder(identifier, advancement);
        ClientboundUpdateAdvancementsPacket add;
        ClientboundUpdateAdvancementsPacket remove;
        if (VersionHelper.isOrAbove26_3) {
            Object positioned = ClientboundUpdateAdvancementsPacketProxy.PositionedAdvancementProxy.INSTANCE.create(holder, 0.0F, 0.0F);
            add = (ClientboundUpdateAdvancementsPacket) ClientboundUpdateAdvancementsPacketProxy.INSTANCE.create(false, List.of(positioned), Set.of(), Map.of(identifier, progress), true);
            remove = (ClientboundUpdateAdvancementsPacket) ClientboundUpdateAdvancementsPacketProxy.INSTANCE.create(false, List.of(), Set.of(identifier), Map.of(), true);
        } else {
            add = new ClientboundUpdateAdvancementsPacket(false, List.of(holder), Set.of(), Map.of(identifier, progress), true);
            remove = new ClientboundUpdateAdvancementsPacket(false, List.of(), Set.of(identifier), Map.of(), true);
        }
        ClientboundBundlePacket packet = new ClientboundBundlePacket(List.of(add, remove));
        player.connection.sendPacket(packet);
    }
}