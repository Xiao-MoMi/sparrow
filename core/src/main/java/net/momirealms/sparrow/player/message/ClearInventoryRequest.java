package net.momirealms.sparrow.player.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.TwoWayRequestMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ClearInventoryRequest extends TwoWayRequestMessage<FriendlyByteBuf, ClearInventoryResponse> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "clear_inventory_request");
    public static final MessageCodec<FriendlyByteBuf, ClearInventoryRequest> CODEC = RedisMessage.codec(ClearInventoryRequest::write, ClearInventoryRequest::new);

    private final UUID playerId;
    private final boolean enderChest;

    public ClearInventoryRequest(@NotNull UUID playerId, boolean enderChest) {
        this.playerId = playerId;
        this.enderChest = enderChest;
    }

    private ClearInventoryRequest(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.enderChest = buffer.readBoolean();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        buffer.writeBoolean(this.enderChest);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @NotNull
    @Override
    protected CompletableFuture<ClearInventoryResponse> handleRequest() {
        return this.execute();
    }

    @NotNull
    public CompletableFuture<ClearInventoryResponse> execute() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer player = plugin.playerManager().getOnlinePlayer(this.playerId);
        ClearInventoryResponse offline = new ClearInventoryResponse(false, 0);
        if (player == null) return CompletableFuture.completedFuture(offline);
        PlatformExecutor executor = plugin.scheduler().platform();
        CompletableFuture<ClearInventoryResponse> result = new CompletableFuture<>();
        result.completeAsync(() -> player.isOnlineLocally() ? this.clear(player) : offline, task -> {
            if (executor.isOwnedByCurrentRegion(player.platformPlayer)) {
                task.run();
            } else {
                executor.run(task, () -> result.complete(offline), player.platformPlayer);
            }
        });
        return result;
    }

    private ClearInventoryResponse clear(BukkitSparrowPlayer target) {
        Player player = target.platformPlayer;
        if (this.enderChest) {
            return new ClearInventoryResponse(true, this.clearInventory(player.getEnderChest()));
        }
        int count = 0;
        InventoryView view = player.getOpenInventory();
        if (view.getType() == InventoryType.CRAFTING || view.getType() == InventoryType.WORKBENCH) {
            // 合成结果是预览, 只累计实际投入的材料.
            Inventory crafting = view.getTopInventory();
            for (int slot = 1; slot < crafting.getSize(); slot++) {
                ItemStack item = crafting.getItem(slot);
                if (item != null && !item.isEmpty()) {
                    count += item.getAmount();
                }
            }
            crafting.clear();
        }
        ItemStack cursor = player.getItemOnCursor();
        if (!cursor.isEmpty()) {
            count += cursor.getAmount();
        }
        player.setItemOnCursor(null);
        // 关闭界面后再清理背包, 包含关闭时退回的物品.
        player.closeInventory();
        count += this.clearInventory(player.getInventory());
        return new ClearInventoryResponse(true, count);
    }

    private int clearInventory(Inventory inventory) {
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.isEmpty()) {
                count += item.getAmount();
            }
        }
        inventory.clear();
        return count;
    }
}