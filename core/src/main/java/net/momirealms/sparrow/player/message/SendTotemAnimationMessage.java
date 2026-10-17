package net.momirealms.sparrow.player.message;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.DeathProtection;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.util.ItemUtils;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SendTotemAnimationMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "send_totem_animation");
    public static final MessageCodec<FriendlyByteBuf, SendTotemAnimationMessage> CODEC = RedisMessage.codec(SendTotemAnimationMessage::write, SendTotemAnimationMessage::new);

    private final UUID playerId;
    private final CompoundTag totem;

    public SendTotemAnimationMessage(@NotNull UUID playerId, @NotNull ItemStack totem) {
        this.playerId = playerId;
        this.totem = ItemUtils.toNbt(CraftItemStack.unwrap(totem));
    }

    private SendTotemAnimationMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        net.momirealms.sparrow.util.FriendlyByteBuf data = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer);
        this.totem = (CompoundTag) data.readNbt(false);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        net.momirealms.sparrow.util.FriendlyByteBuf data = new net.momirealms.sparrow.util.FriendlyByteBuf(buffer);
        data.writeNbt(this.totem, false);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        BukkitSparrowPlayer player = SparrowPlugin.instance().playerManager().getOnlinePlayer(this.playerId);
        if (player == null) return;
        int entityId = player.nmsPlayer.getId();
        net.minecraft.world.item.ItemStack mainHand = player.nmsPlayer.getInventory().getSelectedItem();
        net.minecraft.world.item.ItemStack offHand = player.nmsPlayer.getOffhandItem().copy();
        boolean mainHandTotem = mainHand.has(DataComponents.DEATH_PROTECTION);
        net.minecraft.world.item.ItemStack animation = ItemUtils.fromNbt(this.totem);
        // 解码后的物品属于本次动画, 可以直接补充所需组件.
        if (!animation.has(DataComponents.DEATH_PROTECTION)) {
            animation.set(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING);
        }
        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>(5);
        if (mainHandTotem) {
            packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY))));
        }
        packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.OFFHAND, animation))));
        packets.add(new ClientboundEntityEventPacket(player.nmsPlayer, (byte) 35));
        if (mainHandTotem) {
            packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.MAINHAND, mainHand.copy()))));
        }
        packets.add(new ClientboundSetEquipmentPacket(entityId, List.of(Pair.of(EquipmentSlot.OFFHAND, offHand))));
        player.connection.sendPacket(new ClientboundBundlePacket(packets));
    }
}