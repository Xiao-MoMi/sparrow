package net.momirealms.sparrow.player.teleport;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.world.WorldLocation;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class TeleportManager {
    private final SparrowPlugin plugin;
    private final Cache<UUID, Arrival> arrivals;

    public TeleportManager(@NotNull SparrowPlugin plugin) {
        this(plugin, Ticker.systemTicker());
    }

    TeleportManager(@NotNull SparrowPlugin plugin, @NotNull Ticker ticker) {
        this.plugin = plugin;
        this.arrivals = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).ticker(ticker).build();
    }

    public void onEnable() {
        TeleportRequest.manager(this);
    }

    public boolean prepare(@NotNull UUID player, @NotNull WorldLocation location) {
        if (location.resolve() == null) return false;
        this.arrivals.put(player, new Arrival(location, false));
        return true;
    }

    @Nullable
    public Location consumeSpawn(@NotNull UUID player) {
        Arrival arrival = this.arrivals.asMap().remove(player);
        if (arrival == null) return null;
        Location location = arrival.location.resolve();
        if (location == null || arrival.invalid) {
            // 配置阶段还不能发送游戏聊天, 留到 Join 时提示.
            this.arrivals.put(player, new Arrival(arrival.location, true));
            return null;
        }
        return location;
    }

    public void onJoin(@NotNull Player player) {
        Arrival arrival = this.arrivals.asMap().remove(player.getUniqueId());
        if (arrival != null && arrival.invalid) {
            SparrowPlayer receiver = this.plugin.playerManager().getPlayer(player);
            receiver.sendMessage(this.plugin.translationManager().render((TranslatableComponent) MessageConstants.COMMAND_TP_OFFLINE_INVALID.asComponent(), receiver.locale()));
        }
    }

    public void shutdown() {
        TeleportRequest.manager(null);
        this.arrivals.invalidateAll();
    }

    private record Arrival(WorldLocation location, boolean invalid) {
    }
}
