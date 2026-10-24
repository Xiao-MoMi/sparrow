package net.momirealms.sparrow.player;

import net.momirealms.sparrow.database.PlayerProfileStore;
import net.momirealms.sparrow.database.PlayerNameHistoryStore;
import net.momirealms.sparrow.database.PlayerIpHistoryStore;
import net.momirealms.sparrow.player.listener.PlayerPrepareListener;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;

final class PlayerRecordService implements PlayerPrepareListener {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private PlayerProfileStore profileStore;
    private PlayerNameHistoryStore nameHistoryStore;
    private PlayerIpHistoryStore ipHistoryStore;

    void onEnable() {
        this.profileStore = this.plugin.dataStorage().playerProfileStore();
        this.nameHistoryStore = this.plugin.dataStorage().playerNameHistoryStore();
        this.ipHistoryStore = this.plugin.dataStorage().playerIpHistoryStore();
        this.plugin.playerManager().registerPrepareListener(this);
        this.plugin.playerManager().registerQuitListener(this::onQuit);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onPrepare(@NotNull PlayerConnection connection) {
        long now = System.currentTimeMillis();
        InetAddress ip = connection.ip();
        return CompletableFuture.allOf(
                this.profileStore.saveLogin(connection.uniqueId(), connection.name(), now, ip),
                this.nameHistoryStore.recordIfChanged(connection.uniqueId(), connection.name(), now),
                ip == null ? CompletableFuture.completedFuture(null) : this.ipHistoryStore.recordIfChanged(connection.uniqueId(), ip, now)
        );
    }

    @NotNull
    private CompletableFuture<Void> onQuit(@NotNull BukkitSparrowPlayer player) {
        return this.profileStore.saveLogout(player.uniqueId(), System.currentTimeMillis(), player.getServerLocation());
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onRelease(@NotNull PlayerConnection connection) {
        return CompletableFuture.completedFuture(null);
    }
}