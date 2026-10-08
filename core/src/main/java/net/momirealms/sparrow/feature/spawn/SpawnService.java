package net.momirealms.sparrow.feature.spawn;

import net.momirealms.sparrow.database.SpawnStore;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class SpawnService {
    private final SpawnStore store;
    private final SpawnFeature feature;
    private final String serverId;

    public SpawnService() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        this.store = plugin.dataStorage().spawnStore();
        this.feature = plugin.featureManager().feature(SpawnFeature.ID, SpawnFeature.class);
        this.serverId = ServerConfig.serverId();
    }

    void load() {
        this.feature.setSpawn(this.store.load().join().orElse(null));
    }

    @NotNull
    public CompletableFuture<Void> set(@NotNull Spawn spawn) {
        return this.store.save(spawn).thenCompose(ignored -> {
            this.feature.setSpawn(spawn);
            return SparrowPlugin.instance().messageBrokerManager().publishOneWay(new SpawnMessage(this.serverId, spawn), "").thenApply(receivers -> null);
        });
    }

    @NotNull
    public CompletableFuture<Boolean> delete() {
        return this.store.delete().thenCompose(deleted -> {
            this.feature.setSpawn(null);
            return SparrowPlugin.instance().messageBrokerManager().publishOneWay(new SpawnMessage(this.serverId, null), "").thenApply(receivers -> deleted);
        });
    }
}