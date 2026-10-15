package net.momirealms.sparrow.data;

import net.momirealms.sparrow.data.cache.CacheDefinition;
import net.momirealms.sparrow.data.cache.CachedData;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.feature.home.HomeSnapshot;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.GsonHelper;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@ApiStatus.Internal
public final class HomeDataAccess implements AutoCloseable {
    private final HomeStore store = SparrowPlugin.instance().dataStorage().homeStore();
    private final CachedData<UUID, HomeSnapshot> cache;

    public HomeDataAccess() {
        this.cache = SparrowPlugin.instance().dataAccess().open(new Definition(), Function.identity());
    }

    @NotNull
    public CompletableFuture<HomeSnapshot> read(@NotNull UUID owner) {
        return this.cache.read(owner);
    }

    @Nullable
    public HomeSnapshot readCacheOrNull(@NotNull UUID owner) {
        return this.cache.readCacheOrNull(owner);
    }

    @NotNull
    public HomeSnapshot readCacheJoin(@NotNull UUID owner) {
        return this.cache.readCacheJoin(owner);
    }

    @NotNull
    public CompletableFuture<HomeStore.SaveResult> create(@NotNull Home home) {
        return this.store.create(home).thenCompose(this::saved);
    }

    @NotNull
    public CompletableFuture<HomeStore.SaveResult> update(@NotNull Home home) {
        return this.store.update(home).thenCompose(this::saved);
    }

    // 只在数据库实际保存成功后失效整个所有者快照, 返回任务包含失效与本服在线刷新.
    private CompletableFuture<HomeStore.SaveResult> saved(HomeStore.SaveResult result) {
        if (result.status() != HomeStore.Status.SUCCESS) return CompletableFuture.completedFuture(result);
        return this.cache.invalidate(result.home().owner()).thenApply(ignored -> result);
    }

    @NotNull
    public CompletableFuture<Boolean> delete(@NotNull UUID owner, @NotNull UUID id) {
        return this.store.delete(owner, id).thenCompose(deleted -> {
            if (!deleted) return CompletableFuture.completedFuture(false);
            return this.cache.invalidate(owner).thenApply(ignored -> true);
        });
    }

    // 未指定所有者的批量删除作用于整个 Home 缓存域, 包括离线条目和正在进行的加载.
    @NotNull
    public CompletableFuture<Long> deleteAll(@NotNull HomeStore.Filter filter) {
        return this.store.deleteAll(filter).thenCompose(deleted -> {
            CompletableFuture<Void> invalidation = filter.owner() == null ? this.cache.invalidateAll() : this.cache.invalidate(filter.owner());
            return invalidation.thenApply(ignored -> deleted);
        });
    }

    @Override
    public void close() {
        SparrowPlugin.instance().dataAccess().close(this.cache);
    }

    private final class Definition implements CacheDefinition<UUID, HomeSnapshot> {

        @Override
        @NotNull
        public SparrowKey id() {
            return SparrowKey.sparrow("home");
        }

        @Override
        @NotNull
        public CompletableFuture<HomeSnapshot> load(@NotNull UUID key) {
            return HomeDataAccess.this.store.loadByOwner(key).thenApply(HomeSnapshot::new);
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

        // Redis 只保存记录数组, 排序与名称索引在构造快照时生成; 空数组表示没有 Home.
        @Override
        @NotNull
        public byte[] encodeValue(@NotNull HomeSnapshot value) {
            return GsonHelper.get().toJson(value.homes()).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        @NotNull
        public HomeSnapshot decodeValue(byte @NotNull [] value) {
            Home[] homes = GsonHelper.get().fromJson(new String(value, StandardCharsets.UTF_8), Home[].class);
            return new HomeSnapshot(List.of(homes));
        }
    }
}