package net.momirealms.sparrow.player;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import net.momirealms.sparrow.proxy.bukkit.entity.CraftPlayerProxy;
import net.momirealms.sparrow.proxy.bukkit.util.CraftChatMessageProxy;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.proxy.minecraft.network.ConnectionProxy;
import net.momirealms.sparrow.util.AdventureHelper;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

public final class BukkitSparrowPlayer implements SparrowPlayer {
    private final UUID uniqueId;
    private final String name;
    private final ChannelHandler connection;
    private final Channel channel;
    private volatile Player platformPlayer; // Join 时绑定, Quit 时释放

    BukkitSparrowPlayer(@NotNull ChannelHandler connection, @NotNull UUID uniqueId, @NotNull String name) {
        this.connection = connection;
        this.channel = (Channel) ConnectionProxy.INSTANCE.getChannel(connection);
        this.uniqueId = uniqueId;
        this.name = name;
    }

    void initialize(@NotNull Player player) {
        this.platformPlayer = player;
    }

    void close() {
        this.platformPlayer = null;
    }

    @Override
    @NotNull
    public UUID uniqueId() {
        return this.uniqueId;
    }

    @Override
    @NotNull
    public String name() {
        return this.name;
    }

    @Override
    @NotNull
    public ChannelHandler connection() {
        return this.connection;
    }

    @Override
    @NotNull
    public Channel nettyChannel() {
        return this.channel;
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

    private Player requirePlayer() {
        Player player = this.platformPlayer;
        if (player == null) throw new IllegalStateException("Player has not joined or has already left: " + this.uniqueId);
        return player;
    }
}
