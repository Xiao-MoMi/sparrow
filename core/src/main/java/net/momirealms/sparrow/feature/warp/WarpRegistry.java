package net.momirealms.sparrow.feature.warp;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.momirealms.sparrow.database.WarpStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class WarpRegistry {
    private final WarpStore store;
    private final String serverId;
    private final Consumer<WarpMessage> publisher;
    private final ConcurrentChainedObject2ObjectHashTable<UUID, Warp> byId = new ConcurrentChainedObject2ObjectHashTable<>();
    private final ConcurrentChainedObject2ObjectHashTable<String, Warp> byKey = new ConcurrentChainedObject2ObjectHashTable<>();
    private volatile Index index = Index.EMPTY; // 按名称键排好序的快照, 供补全和列表使用

    public WarpRegistry(@NotNull WarpStore store, @NotNull String serverId, @NotNull Consumer<WarpMessage> publisher) {
        this.store = store;
        this.serverId = serverId;
        this.publisher = publisher;
    }

    /**
     * 整表读入并替换内存副本.
     * <strong>会阻塞调用线程直到读取完成</strong>.
     */
    public void load() {
        this.replaceAll(this.store.loadAll().join());
    }

    @Nullable
    public Warp get(@NotNull String name) {
        return this.byKey.get(Warp.key(name));
    }

    @Nullable
    public Warp get(@NotNull UUID id) {
        return this.byId.get(id);
    }

    public int size() {
        return this.index.warps.length;
    }

    @NotNull
    public List<Warp> all() {
        return Arrays.asList(this.index.warps);
    }

    /**
     * 按前缀补全名称, 忽略大小写, 按名称键顺序最多返回 {@code limit} 个.
     *
     * @param visible 只有通过筛选的 warp 会出现在结果中
     */
    @NotNull
    public List<String> complete(@NotNull String input, @NotNull Predicate<Warp> visible, int limit) {
        Index index = this.index;
        String prefix = Warp.key(input);
        // 定位到第一个不小于前缀的名称键, 之后连续一段都以前缀开头
        int start = Arrays.binarySearch(index.keys, prefix);
        if (start < 0) start = -start - 1;
        List<String> result = new ArrayList<>(Math.min(limit, 16));
        for (int i = start; i < index.keys.length && result.size() < limit; i++) {
            if (!index.keys[i].startsWith(prefix)) break;
            Warp warp = index.warps[i];
            if (visible.test(warp)) result.add(warp.name());
        }
        return result;
    }

    /**
     * 写入数据库, 成功后更新内存并通知其他服务器.
     *
     * @return 写入任务, 名称已被另一个 warp 占用时结果为 false
     */
    @NotNull
    public CompletableFuture<Boolean> save(@NotNull Warp warp) {
        return this.store.save(warp).thenApply(saved -> {
            if (saved) {
                this.put(warp);
                this.publisher.accept(WarpMessage.save(this.serverId, warp));
            }
            return saved;
        });
    }

    @NotNull
    public CompletableFuture<Boolean> delete(@NotNull UUID id) {
        return this.store.delete(id).thenApply(deleted -> {
            if (deleted) {
                this.remove(id);
                this.publisher.accept(WarpMessage.delete(this.serverId, id));
            }
            return deleted;
        });
    }

    // 批量删除后整表重读, 其他服务器也各自重读
    @NotNull
    public CompletableFuture<Integer> deleteByWorld(@NotNull String server, @NotNull String world) {
        return this.store.deleteByWorld(server, world).thenCompose(this::reloadAfterBulkDelete);
    }

    @NotNull
    public CompletableFuture<Integer> deleteByServer(@NotNull String server) {
        return this.store.deleteByServer(server).thenCompose(this::reloadAfterBulkDelete);
    }

    private CompletableFuture<Integer> reloadAfterBulkDelete(int deleted) {
        if (deleted == 0) return CompletableFuture.completedFuture(0);
        return this.store.loadAll().thenApply(warps -> {
            this.replaceAll(warps);
            this.publisher.accept(WarpMessage.reload(this.serverId));
            return deleted;
        });
    }

    /**
     * 处理其他服务器的变更通知, 在 Redis 线程上调用.
     */
    public void accept(@NotNull WarpMessage message) {
        if (message.origin().equals(this.serverId)) return;
        switch (message.type()) {
            case SAVE -> this.put(message.warp());
            case DELETE -> this.remove(message.id());
            case RELOAD -> this.store.loadAll().thenAccept(this::replaceAll);
        }
    }

    // 写入只来自本服命令和 Redis 通知, 频率很低, 串行执行后重建排序快照
    private synchronized void put(Warp warp) {
        Warp current = this.byId.get(warp.id());
        // 来得更晚的旧通知不覆盖新数据
        if (current != null && current.updatedAt() > warp.updatedAt()) return;
        if (current != null) this.byKey.remove(current.key(), current);
        Warp occupant = this.byKey.put(warp.key(), warp);
        // 数据库保证名称唯一, 本地同名的另一条是还没收到改名或删除通知的旧数据
        if (occupant != null && !occupant.id().equals(warp.id())) this.byId.remove(occupant.id(), occupant);
        this.byId.put(warp.id(), warp);
        // 同名的旧条目在插入时被替换
        Index index = this.index;
        if (current != null) index = index.without(current.key());
        this.index = index.with(warp);
    }

    private synchronized void remove(UUID id) {
        Warp removed = this.byId.remove(id);
        if (removed == null) return;
        this.byKey.remove(removed.key(), removed);
        this.index = this.index.without(removed.key());
    }

    // 先写入新数据再删除已不存在的条目, 重读期间的查找不会落空
    private synchronized void replaceAll(List<Warp> warps) {
        int size = warps.size();
        Set<UUID> ids = new HashSet<>(size * 2);
        for (int i = 0; i < size; i++) {
            Warp warp = warps.get(i);
            ids.add(warp.id());
            Warp previous = this.byId.put(warp.id(), warp);
            if (previous != null && !previous.key().equals(warp.key())) this.byKey.remove(previous.key(), previous);
            this.byKey.put(warp.key(), warp);
        }
        List<Warp> stale = new ArrayList<>();
        for (Warp warp : this.byId.values()) {
            if (!ids.contains(warp.id())) stale.add(warp);
        }
        for (int i = 0; i < stale.size(); i++) {
            Warp warp = stale.get(i);
            this.byId.remove(warp.id(), warp);
            this.byKey.remove(warp.key(), warp);
        }
        this.index = Index.of(this.byId.values());
    }

    // warps 与 keys 下标一一对应并按名称键排序, 修改时生成新快照整体替换, 读取时不需要加锁
    private record Index(Warp[] warps, String[] keys) {
        private static final Index EMPTY = new Index(new Warp[0], new String[0]);

        // 名称键先算好再排序, 比较时不再反复转小写
        private static Index of(Collection<Warp> values) {
            Warp[] warps = values.toArray(new Warp[0]);
            String[] keys = new String[warps.length];
            Integer[] order = new Integer[warps.length];
            for (int i = 0; i < warps.length; i++) {
                keys[i] = warps[i].key();
                order[i] = i;
            }
            Arrays.sort(order, Comparator.comparing(i -> keys[i]));
            Warp[] sortedWarps = new Warp[warps.length];
            String[] sortedKeys = new String[warps.length];
            for (int i = 0; i < order.length; i++) {
                sortedWarps[i] = warps[order[i]];
                sortedKeys[i] = keys[order[i]];
            }
            return new Index(sortedWarps, sortedKeys);
        }

        // 名称键已存在时替换该条, 否则插入到排序位置
        private Index with(Warp warp) {
            String key = warp.key();
            int found = Arrays.binarySearch(this.keys, key);
            if (found >= 0) {
                Warp[] warps = this.warps.clone();
                warps[found] = warp;
                return new Index(warps, this.keys);
            }
            int at = -found - 1;
            int length = this.warps.length;
            Warp[] warps = new Warp[length + 1];
            String[] keys = new String[length + 1];
            System.arraycopy(this.warps, 0, warps, 0, at);
            System.arraycopy(this.keys, 0, keys, 0, at);
            warps[at] = warp;
            keys[at] = key;
            System.arraycopy(this.warps, at, warps, at + 1, length - at);
            System.arraycopy(this.keys, at, keys, at + 1, length - at);
            return new Index(warps, keys);
        }

        private Index without(String key) {
            int found = Arrays.binarySearch(this.keys, key);
            if (found < 0) return this;
            int length = this.warps.length;
            Warp[] warps = new Warp[length - 1];
            String[] keys = new String[length - 1];
            System.arraycopy(this.warps, 0, warps, 0, found);
            System.arraycopy(this.keys, 0, keys, 0, found);
            System.arraycopy(this.warps, found + 1, warps, found, length - found - 1);
            System.arraycopy(this.keys, found + 1, keys, found, length - found - 1);
            return new Index(warps, keys);
        }
    }
}
