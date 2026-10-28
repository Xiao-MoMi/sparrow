package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.proxy.bukkit.inventory.MenuTypeProxy;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class OpenContainerMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "open_container");
    public static final MessageCodec<FriendlyByteBuf, OpenContainerMessage> CODEC = RedisMessage.codec(OpenContainerMessage::write, OpenContainerMessage::new);

    private final UUID playerId;
    private final ContainerType type;
    private final String title;

    public OpenContainerMessage(@NotNull UUID playerId, @NotNull ContainerType type) {
        this(playerId, type, null);
    }

    public OpenContainerMessage(@NotNull UUID playerId, @NotNull ContainerType type, @Nullable String title) {
        this.playerId = playerId;
        this.type = type;
        this.title = title;
    }

    private OpenContainerMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.type = ContainerType.valueOf(ByteBufHelper.readUtf8(buffer, 64));
        this.title = buffer.readBoolean() ? ByteBufHelper.readUtf8(buffer, 262144) : null;
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        ByteBufHelper.writeUtf8(buffer, this.type.name(), 64);
        buffer.writeBoolean(this.title != null);
        if (this.title != null) {
            ByteBufHelper.writeUtf8(buffer, this.title, 262144);
        }
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
            this.type.open(player.platformPlayer, this.title);
        };
        if (executor.isOwnedByCurrentRegion(player.platformPlayer)) {
            open.run();
        } else {
            executor.run(open, () -> {}, player.platformPlayer);
        }
    }

    public enum ContainerType {
        ANVIL(MenuType.ANVIL, InventoryType.ANVIL),
        CARTOGRAPHY_TABLE(MenuType.CARTOGRAPHY_TABLE, InventoryType.CARTOGRAPHY),
        ENCHANTMENT_TABLE(MenuType.ENCHANTMENT, InventoryType.ENCHANTING),
        ENDER_CHEST(null, InventoryType.ENDER_CHEST),
        GRINDSTONE(MenuType.GRINDSTONE, InventoryType.GRINDSTONE),
        LOOM(MenuType.LOOM, InventoryType.LOOM),
        SMITHING_TABLE(MenuType.SMITHING, InventoryType.SMITHING),
        STONECUTTER(MenuType.STONECUTTER, InventoryType.STONECUTTER),
        WORKBENCH(MenuType.CRAFTING, InventoryType.WORKBENCH);

        private final MenuType.Typed<?, ?> menuType;
        private final InventoryType inventoryType;

        ContainerType(@Nullable MenuType.Typed<?, ?> menuType, @NotNull InventoryType inventoryType) {
            this.menuType = menuType;
            this.inventoryType = inventoryType;
        }

        public void open(@NotNull Player player) {
            this.open(player, null);
        }

        public boolean open(@NotNull Player player, @Nullable String input) {
            Component title = input == null ? null : AdventureHelper.miniMessage(input, player);
            if (this == ENDER_CHEST) {
                return this.openEnderChest(player, title);
            }
            InventoryView view = title == null
                    ? this.menuType.create(player, this.inventoryType.getDefaultTitle())
                    : MenuTypeProxy.INSTANCE.create(this.menuType, player, AdventureHelper.toNativeComponent(title));
            player.openInventory(view);
            return true;
        }

        private boolean openEnderChest(Player player, @Nullable Component title) {
            if (title == null) return player.openInventory(player.getEnderChest()) != null;
            ServerPlayer minecraftPlayer = ((CraftPlayer) player).getHandle();
            net.minecraft.network.chat.Component minecraftTitle = CraftChatMessage.fromJSON(AdventureHelper.componentToJson(title));
            return minecraftPlayer.openMenu(new SimpleMenuProvider((id, inventory, owner) -> {
                ChestMenu menu = ChestMenu.threeRows(id, inventory, minecraftPlayer.getEnderChestInventory());
                menu.checkReachable = false;
                return menu;
            }, minecraftTitle)).isPresent();
        }
    }
}