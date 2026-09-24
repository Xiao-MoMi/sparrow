package net.momirealms.sparrow.player;

import io.netty.channel.ChannelHandler;
import net.kyori.adventure.text.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
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
                new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut),
                new ClientboundSetSubtitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(subtitle))),
                new ClientboundSetTitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(title)))
        )));
    }

    @Override
    public void sendTotemAnimation(@NotNull ItemStack totem) {
        Player player = this.requirePlayer();
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        int slot = player.getInventory().getHeldItemSlot();
        net.minecraft.world.item.ItemStack animation = CraftItemStack.asNMSCopy(totem);
        animation.set(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING);
        this.sendPacket(new ClientboundBundlePacket(List.of(
                new ClientboundSetPlayerInventoryPacket(slot, animation),
                new ClientboundEntityEventPacket(handle, (byte) 35),
                new ClientboundSetPlayerInventoryPacket(slot, handle.getInventory().getItem(slot).copy())
        )));
    }

    @Override
    public void sendDemo() {
        this.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, 0.0f));
    }

    @Override
    public void sendCredits() {
        this.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 1.0f));
    }

    private Player requirePlayer() {
        Player player = this.platformPlayer;
        if (player == null) {
            throw new IllegalStateException("Player has not joined or has already left: " + this.uniqueId());
        }
        return player;
    }
}
