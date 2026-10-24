package net.momirealms.sparrow.player;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.momirealms.sparrow.database.PlayerProfileStore;
import net.momirealms.sparrow.player.listener.PlayerPrepareListener;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class PlayerProfileService implements PlayerPrepareListener {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, PlayerProfile> profiles = new ConcurrentChainedObject2ObjectHashTable<>();
    private PlayerProfileStore store;

    void onEnable() {
        this.store = this.plugin.dataStorage().playerProfileStore();
        this.plugin.playerManager().registerPrepareListener(this);
        this.plugin.playerManager().registerQuitListener(this::onQuit);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onPrepare(@NotNull PlayerConnection connection) {
        return this.store.load(connection.uniqueId()).thenCompose(found -> {
            PlayerProfile previous = found.orElse(null);
            PlayerProfile profile = new PlayerProfile(
                    connection.name(),
                    System.currentTimeMillis(),
                    previous == null ? 0 : previous.logoutAt(),
                    connection.ip(),
                    previous == null ? null : previous.logoutLocation()
            );
            return this.store.save(connection.uniqueId(), profile).thenRun(() -> this.profiles.put(connection.uniqueId(), profile));
        });
    }

    @NotNull
    private CompletableFuture<Void> onQuit(@NotNull BukkitSparrowPlayer player) {
        PlayerProfile previous = this.profiles.get(player.uniqueId());
        PlayerProfile profile = new PlayerProfile(previous.name(), previous.loginAt(), System.currentTimeMillis(), previous.ip(), player.getServerLocation());
        return this.store.save(player.uniqueId(), profile);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onRelease(@NotNull PlayerConnection connection) {
        this.profiles.remove(connection.uniqueId());
        return CompletableFuture.completedFuture(null);
    }
}