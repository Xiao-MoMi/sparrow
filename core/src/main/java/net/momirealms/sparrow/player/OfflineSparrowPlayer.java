package net.momirealms.sparrow.player;

import net.momirealms.sparrow.cluster.PlayerPresence;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class OfflineSparrowPlayer implements SparrowPlayer {
    private final UUID uniqueId;
    private final String name;

    OfflineSparrowPlayer(@NotNull UUID uniqueId, @NotNull String name) {
        this.uniqueId = uniqueId;
        this.name = name;
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
    public boolean isOnlineLocally() {
        return SparrowPlugin.instance().playerManager().getPlayer(this.uniqueId) != null;
    }

    @Override
    public boolean isOnlineGlobally() {
        return SparrowPlugin.instance().playerDirectory().find(this.uniqueId) != null;
    }

    @Override
    @Nullable
    public String getCurrentServerId() {
        PlayerPresence presence = SparrowPlugin.instance().playerDirectory().find(this.uniqueId);
        return presence == null ? null : presence.server();
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> checkPermission(@NotNull String permission) {
        return SparrowPlugin.instance().compatibilityManager().checkPermission(this.uniqueId, permission);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerData>> loadData() {
        return SparrowPlugin.instance().dataStorage().loadPlayer(this.uniqueId);
    }
}
