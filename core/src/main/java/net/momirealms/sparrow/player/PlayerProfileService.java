package net.momirealms.sparrow.player;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.momirealms.sparrow.data.access.SingleWriterData;
import net.momirealms.sparrow.data.access.SingleWriterEntries;
import net.momirealms.sparrow.data.definition.SingleWriterDefinition;
import net.momirealms.sparrow.data.storage.DataCodec;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.ServerLocation;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.UUIDUtils;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class PlayerProfileService {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final ConcurrentChainedObject2ObjectHashTable<PlayerConnection, SingleWriterEntries<String, PlayerProfile>> profiles = new ConcurrentChainedObject2ObjectHashTable<>();
    private SingleWriterData<UUID, String, PlayerProfile> data;

    void onEnable() {
        this.data = this.plugin.dataAccess().register(SingleWriterDefinition.of(
                SparrowKey.sparrow("player_profile"),
                DataCodec.of(UUIDUtils::toBytes, UUIDUtils::fromBytes),
                DataCodec.of(entry -> entry.getBytes(StandardCharsets.UTF_8), bytes -> new String(bytes, StandardCharsets.UTF_8)),
                PlayerProfile.Codec.INSTANCE,
                this.plugin.dataStorage().playerProfileStore()
        ).redisTtl(Duration.ofMinutes(10)));
    }

    @NotNull
    CompletableFuture<Void> prepare(@NotNull PlayerConnection connection) {
        return this.data.load(connection.uniqueId()).thenAccept(profile -> this.profiles.put(connection, profile));
    }

    void onJoin(@NotNull BukkitSparrowPlayer player) {
        SingleWriterEntries<String, PlayerProfile> entries = this.profiles.get(player.connection);
        long loginAt = System.currentTimeMillis();
        entries.update(PlayerProfile.ENTRY, previous -> new PlayerProfile(
                player.name(),
                loginAt,
                previous == null ? 0 : previous.logoutAt(),
                player.connection.ip(),
                previous == null ? null : previous.logoutLocation()
        ));
        entries.save().whenComplete((ignored, error) -> {
            if (error != null) {
                this.plugin.logger().warn("Failed to save player profile after join for " + player.name(), error);
            }
        });
    }

    void onQuit(@NotNull BukkitSparrowPlayer player) {
        long logoutAt = System.currentTimeMillis();
        ServerLocation location = player.getServerLocation();
        this.profiles.get(player.connection).update(PlayerProfile.ENTRY, profile -> new PlayerProfile(profile.name(), profile.loginAt(), logoutAt, profile.ip(), location));
    }

    @NotNull
    CompletableFuture<Void> release(@NotNull PlayerConnection connection) {
        // 准备失败的连接可能尚未取得档案写容器
        SingleWriterEntries<String, PlayerProfile> entries = this.profiles.remove(connection);
        if (entries == null) return CompletableFuture.completedFuture(null);
        return entries.save()
                .handle((ignored, error) -> {
                    if (error != null) {
                        this.plugin.logger().warn("Failed to save player profile for " + connection.name(), error);
                    }
                    return null;
                })
                .thenCompose(ignored -> this.data.release(connection.uniqueId()));
    }

    @NotNull
    SingleWriterData<UUID, String, PlayerProfile> data() {
        return this.data;
    }
}