package net.momirealms.sparrow.player.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.MenuType;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class OpenContainerMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "open_container");
    public static final MessageCodec<FriendlyByteBuf, OpenContainerMessage> CODEC = RedisMessage.codec(OpenContainerMessage::write, OpenContainerMessage::new);

    private final UUID playerId;
    private final ContainerType type;

    public OpenContainerMessage(@NotNull UUID playerId, @NotNull ContainerType type) {
        this.playerId = playerId;
        this.type = type;
    }

    private OpenContainerMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.type = ContainerType.valueOf(ByteBufHelper.readUtf8(buffer, 64));
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        ByteBufHelper.writeUtf8(buffer, this.type.name(), 64);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer player = plugin.playerManager().getOnlinePlayer(this.playerId);
        if (player == null) return;
        PlatformExecutor executor = plugin.scheduler().platform();
        Runnable open = () -> {
            if (!player.isOnlineLocally()) return;
            this.type.open(player.platformPlayer);
        };
        if (executor.isOwnedByCurrentRegion(player.platformPlayer)) {
            open.run();
        } else {
            executor.run(open, () -> {}, player.platformPlayer);
        }
    }

    public enum ContainerType {
        ANVIL,
        CARTOGRAPHY_TABLE,
        ENCHANTMENT_TABLE,
        GRINDSTONE,
        LOOM,
        SMITHING_TABLE,
        STONECUTTER,
        WORKBENCH;

        public void open(@NotNull Player player) {
            switch (this) {
                case ANVIL -> player.openInventory(MenuType.ANVIL.create(player, InventoryType.ANVIL.getDefaultTitle()));
                case CARTOGRAPHY_TABLE -> player.openInventory(MenuType.CARTOGRAPHY_TABLE.create(player, InventoryType.CARTOGRAPHY.getDefaultTitle()));
                case ENCHANTMENT_TABLE -> player.openInventory(MenuType.ENCHANTMENT.create(player, InventoryType.ENCHANTING.getDefaultTitle()));
                case GRINDSTONE -> player.openInventory(MenuType.GRINDSTONE.create(player, InventoryType.GRINDSTONE.getDefaultTitle()));
                case LOOM -> player.openInventory(MenuType.LOOM.create(player, InventoryType.LOOM.getDefaultTitle()));
                case SMITHING_TABLE -> player.openInventory(MenuType.SMITHING.create(player, InventoryType.SMITHING.getDefaultTitle()));
                case STONECUTTER -> player.openInventory(MenuType.STONECUTTER.create(player, InventoryType.STONECUTTER.getDefaultTitle()));
                case WORKBENCH -> player.openInventory(MenuType.CRAFTING.create(player, InventoryType.WORKBENCH.getDefaultTitle()));
            }
        }
    }
}