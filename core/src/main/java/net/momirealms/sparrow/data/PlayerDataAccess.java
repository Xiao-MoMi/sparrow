package net.momirealms.sparrow.data;

import net.momirealms.sparrow.data.cache.CacheDefinition;
import net.momirealms.sparrow.data.cache.CachedData;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.GsonHelper;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@ApiStatus.Internal
public final class PlayerDataAccess {
    private final DataStorage storage = SparrowPlugin.instance().dataStorage();
    private final CachedData<UUID, Optional<PlayerData>> cache;

    public PlayerDataAccess() {
        this.cache = SparrowPlugin.instance().dataAccess().open(new Definition(), Function.identity());
    }

    // 准入检查通过后记录本次登录, 提交并使旧缓存失效后完成档案预加载.
    @NotNull
    public CompletableFuture<PlayerData> prepareLogin(@NotNull UUID player, @NotNull String name, long ip, long timestamp) {
        return this.storage.saveLogin(player, name, ip, timestamp)
                .thenCompose(ignored -> this.cache.invalidate(player))
                .thenCompose(ignored -> this.cache.read(player))
                .thenApply(Optional::orElseThrow);
    }

    @NotNull
    public CompletableFuture<Optional<PlayerData>> read(@NotNull UUID player) {
        return this.cache.read(player);
    }

    // 未加载或没有档案时返回 null, 已有快照可在在线刷新期间继续用于展示.
    @Nullable
    public PlayerData readCacheOrNull(@NotNull UUID player) {
        Optional<PlayerData> cached = this.cache.readCacheOrNull(player);
        return cached == null ? null : cached.orElse(null);
    }

    @NotNull
    public Optional<PlayerData> readCacheJoin(@NotNull UUID player) {
        return this.cache.readCacheJoin(player);
    }

    // 退出记录提交后通知各服重新读取, 数据库负责按事件时间合并字段.
    @NotNull
    public CompletableFuture<Void> saveLogout(@NotNull UUID player, @NotNull String name, long timestamp, @NotNull String server, @NotNull WorldLocation location) {
        return this.storage.saveLogout(player, name, timestamp, server, location)
                .thenCompose(ignored -> this.cache.invalidate(player));
    }

    @NotNull
    public CompletableFuture<Void> saveDeath(@NotNull UUID player, @NotNull String name, long timestamp, @NotNull String server, @NotNull WorldLocation location) {
        return this.storage.saveDeath(player, name, timestamp, server, location)
                .thenCompose(ignored -> this.cache.invalidate(player));
    }

    private final class Definition implements CacheDefinition<UUID, Optional<PlayerData>> {

        @Override
        @NotNull
        public SparrowKey id() {
            return SparrowKey.sparrow("player-data");
        }

        @Override
        @NotNull
        public CompletableFuture<Optional<PlayerData>> load(@NotNull UUID key) {
            return PlayerDataAccess.this.storage.loadPlayer(key);
        }

        @Override
        @NotNull
        public String encodeKey(@NotNull UUID key) {
            return key.toString();
        }

        @Override
        @NotNull
        public UUID decodeKey(@NotNull String key) {
            return UUID.fromString(key);
        }

        // JSON null 表示数据库中没有档案, 与 Redis 中没有缓存条目区分.
        @Override
        @NotNull
        public byte[] encodeValue(@NotNull Optional<PlayerData> value) {
            return GsonHelper.get().toJson(value.orElse(null)).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        @NotNull
        public Optional<PlayerData> decodeValue(byte @NotNull [] value) {
            return Optional.ofNullable(GsonHelper.get().fromJson(new String(value, StandardCharsets.UTF_8), PlayerData.class));
        }
    }
}