package net.momirealms.sparrow.player;

import net.momirealms.sparrow.data.access.SingleWriterData;
import net.momirealms.sparrow.data.access.SingleWriterEntries;
import net.momirealms.sparrow.data.definition.SingleWriterDefinition;
import net.momirealms.sparrow.data.storage.DataCodec;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.ServerLocation;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class PlayerProfileService {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private SingleWriterData<String, PlayerProfile> data;

    void onEnable() {
        this.data = this.plugin.dataAccess().register(SingleWriterDefinition.of(
                SparrowKey.sparrow("player_profile"),
                DataCodec.of(entry -> entry.getBytes(StandardCharsets.UTF_8), bytes -> new String(bytes, StandardCharsets.UTF_8)),
                PlayerProfile.Codec.INSTANCE,
                this.plugin.dataStorage().playerProfileStore()
        ).redisTtl(Duration.ofMinutes(10)));
    }

    // 档案写容器在准备阶段已加载完成, 进服和退出时 join 不会等待
    void onJoin(@NotNull BukkitSparrowPlayer player) {
        SingleWriterEntries<String, PlayerProfile> entries = player.connection.load(this.data).join();
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
        player.connection.load(this.data).join().update(PlayerProfile.ENTRY, profile -> new PlayerProfile(profile.name(), profile.loginAt(), logoutAt, profile.ip(), location));
    }

    @NotNull
    SingleWriterData<String, PlayerProfile> data() {
        return this.data;
    }
}