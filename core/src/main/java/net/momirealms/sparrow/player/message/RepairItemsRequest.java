package net.momirealms.sparrow.player.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.player.message.RepairItemsResponse.Status;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class RepairItemsRequest extends TwoWayRequestMessage<FriendlyByteBuf, RepairItemsResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "repair_items_request");
    public static final MessageCodec<FriendlyByteBuf, RepairItemsRequest> CODEC = RedisMessage.codec(RepairItemsRequest::write, RepairItemsRequest::new);

    private final UUID playerId;
    private final boolean all;
    private final EquipmentSlot slot;
    private final int value;

    public RepairItemsRequest(@NotNull UUID playerId, boolean all, @NotNull EquipmentSlot slot, int value) {
        this.playerId = playerId;
        this.all = all;
        this.slot = slot;
        this.value = value;
    }

    private RepairItemsRequest(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.all = buffer.readBoolean();
        this.slot = buffer.readEnum(EquipmentSlot.class);
        this.value = buffer.readInt();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        buffer.writeBoolean(this.all);
        buffer.writeEnum(this.slot);
        buffer.writeInt(this.value);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @NotNull
    @Override
    protected CompletableFuture<RepairItemsResponse> handleRequest() {
        return this.execute();
    }

    @NotNull
    public CompletableFuture<RepairItemsResponse> execute() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer player = plugin.playerManager().getOnlinePlayer(this.playerId);
        RepairItemsResponse offline = new RepairItemsResponse(Status.OFFLINE, 0, 0, 0, 0);
        if (player == null) return CompletableFuture.completedFuture(offline);
        PlatformExecutor executor = plugin.scheduler().platform();
        CompletableFuture<RepairItemsResponse> result = new CompletableFuture<>();
        result.completeAsync(() -> player.isOnlineLocally() ? this.repair(player) : offline, task -> {
            if (executor.isOwnedByCurrentRegion(player.platformPlayer)) {
                task.run();
            } else {
                executor.run(task, () -> result.complete(offline), player.platformPlayer);
            }
        });
        return result;
    }

    private RepairItemsResponse repair(BukkitSparrowPlayer target) {
        if (this.all) {
            PlayerInventory inventory = target.platformPlayer.getInventory();
            int count = 0;
            int restored = 0;
            for (int index = 0; index < inventory.getSize(); index++) {
                ItemStack item = CraftItemStack.asNMSCopy(inventory.getItem(index));
                if (item.isEmpty() || item.getMaxDamage() == 0 || item.getDamageValue() == 0) continue;
                count += item.getCount();
                restored += item.getDamageValue() * item.getCount();
                item.setDamageValue(0);
                inventory.setItem(index, CraftItemStack.asBukkitCopy(item));
            }
            return new RepairItemsResponse(Status.SUCCESS, count, restored, 0, 0);
        }
        EntityEquipment equipment = target.platformPlayer.getEquipment();
        ItemStack item = CraftItemStack.asNMSCopy(equipment.getItem(this.slot));
        if (item.isEmpty()) return new RepairItemsResponse(Status.ITEMLESS, 0, 0, 0, 0);
        int maximum = item.getMaxDamage();
        if (maximum == 0) return new RepairItemsResponse(Status.NOT_DAMAGEABLE, 0, 0, 0, 0);
        int previous = item.getDamageValue();
        int damage = this.value < 0 ? 0 : Math.max(0, previous - this.value);
        int count = previous == damage ? 0 : item.getCount();
        if (count > 0) {
            item.setDamageValue(damage);
            equipment.setItem(this.slot, CraftItemStack.asBukkitCopy(item));
        }
        return new RepairItemsResponse(Status.SUCCESS, count, (previous - damage) * count, maximum - damage, maximum);
    }
}