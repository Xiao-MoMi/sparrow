package net.momirealms.sparrow.feature.head;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.DurationUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class HeadFeature extends Feature<HeadSettings> {
    public static final String ID = "head";

    private final SparrowPlugin plugin;
    private volatile Session session;
    private volatile long generation;

    public HeadFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        HeadSettings settings = this.plugin.configurationManager().featuresConfig().config().head();
        settings.validate();
        this.config = settings;
    }

    @Override
    protected synchronized void onEnable() {
        this.session = new Session(this.config);
    }

    @Override
    protected synchronized void onDisable() {
        this.generation++;
        Session previous = this.session;
        this.session = null;
        if (previous != null) previous.close();
    }

    @Override
    protected void onUnload() {
        Session previous = this.session;
        if (previous != null) previous.close();
    }

    public long generation() { return this.generation; }

    /** 异步查询名称; 无资料时为 null, force 跳过两层缓存并刷新成功结果. */
    @NotNull
    public CompletableFuture<@Nullable HeadData> fetchByName(@NotNull String name, boolean force) {
        return this.fetch(new Query("name:" + name.toLowerCase(Locale.ROOT), name, null), force);
    }

    /** 异步查询 UUID; 无资料时为 null, force 跳过两层缓存并刷新成功结果. */
    @NotNull
    public CompletableFuture<@Nullable HeadData> fetchByUuid(@NotNull UUID uuid, boolean force) {
        return this.fetch(new Query("uuid:" + uuid, null, uuid), force);
    }

    private CompletableFuture<HeadData> fetch(Query query, boolean force) {
        Session current = this.session;
        if (current == null || !this.enabled()) return CompletableFuture.failedFuture(new CancellationException("Head feature is disabled"));
        return current.fetch(query, force);
    }

    /** 在接收玩家线程分组掉落头颅; 请求已失效或玩家已退出时返回 false. */
    public synchronized boolean give(@NotNull Player player, @NotNull HeadData data, int amount, long expectedGeneration) {
        if (!this.enabled() || this.generation != expectedGeneration) return false;
        BukkitSparrowPlayer receiver = this.plugin.playerManager().getPlayer(player);
        if (receiver == null || !player.isOnline()) return false;
        int remaining = amount;
        while (remaining > 0) {
            int count = Math.min(64, remaining);
            receiver.dropItem(HeadItems.create(data, count));
            remaining -= count;
        }
        return true;
    }

    // 在线玩家 Profile 在其所属线程读取, 查询线程等待这个快照.
    private HeadData online(Query query) throws Exception {
        BukkitSparrowPlayer target = null;
        if (query.uuid != null) {
            target = (BukkitSparrowPlayer) this.plugin.playerManager().getPlayer(query.uuid);
        } else {
            for (SparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
                if (player.name().equalsIgnoreCase(query.name)) {
                    target = (BukkitSparrowPlayer) player;
                    break;
                }
            }
        }
        if (target == null) return null;
        BukkitSparrowPlayer selected = target;
        CompletableFuture<HeadData> result = new CompletableFuture<>();
        this.plugin.scheduler().platform().run(() -> {
            if (result.isDone()) return;
            try {
                result.complete(this.plugin.playerManager().getPlayer(selected.platformPlayer()) == selected
                        ? HeadItems.fromProfile(selected.nmsPlayer().getGameProfile()) : null);
            } catch (RuntimeException exception) {
                result.completeExceptionally(exception);
            }
        }, () -> result.complete(null), selected.platformPlayer());
        try {
            return result.get();
        } finally {
            result.cancel(false);
        }
    }

    private record Query(String key, String name, UUID uuid) {
    }

    private final class Session implements AutoCloseable {
        private final HeadSettings settings;
        private final HeadCache cache;
        private final HttpClient http;
        private final ProfileClient profiles;
        private final ExecutorService executor;
        private final Map<String, Request> inFlight = new HashMap<>();
        private final Set<Request> requests = new HashSet<>();
        private boolean closed;

        private Session(HeadSettings settings) {
            this.settings = settings;
            this.cache = new HeadCache(settings, settings.cache().redis().enabled() ? HeadFeature.this.plugin.redisConnector().connection() : null, HeadFeature.this.plugin.logger());
            this.http = HttpClient.newBuilder().connectTimeout(DurationUtils.parsePositive(settings.api().connectTimeout())).build();
            this.profiles = new ProfileClient(this.http, settings.api());
            this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("sparrow-head-", 0).factory());
        }

        private synchronized CompletableFuture<HeadData> fetch(Query query, boolean force) {
            if (this.closed) return CompletableFuture.failedFuture(new CancellationException("Head feature is disabled"));
            Request existing = this.inFlight.get(query.key);
            if (existing != null && (!force || existing.force)) return existing.result.copy();
            Request request = new Request(query, force);
            this.inFlight.put(query.key, request);
            this.requests.add(request);
            long timeout = DurationUtils.parsePositive(this.settings.requestTimeout()).toMillis();
            request.result.orTimeout(timeout, TimeUnit.MILLISECONDS).whenComplete((value, error) -> {
                if (error != null) request.task.cancel(true);
                synchronized (this) {
                    this.inFlight.remove(query.key, request);
                    this.requests.remove(request);
                }
            });
            this.executor.execute(request.task);
            return request.result.copy();
        }

        private HeadData lookup(Request request) throws Exception {
            Query query = request.query;
            if (query.name != null && !query.name.matches("[a-zA-Z0-9_]{1,16}")) {
                throw new HeadFetchException(HeadFetchException.Reason.INVALID_INPUT, "Invalid player name");
            }
            List<String> sources = this.settings.sourceOrder();
            for (int i = 0; i < sources.size(); i++) {
                if (sources.get(i).equals("online")) {
                    HeadData data = HeadFeature.this.online(query);
                    if (data != null) return data;
                } else {
                    HeadData data = request.force ? null : this.cache.get(query.key);
                    if (data != null) return data;
                    data = query.uuid == null ? this.profiles.fetchByName(query.name) : this.profiles.fetchByUuid(query.uuid);
                    if (data != null) {
                        this.store(request, data, List.of("name:" + data.name().toLowerCase(Locale.ROOT), "uuid:" + data.uuid()));
                        return data;
                    }
                }
            }
            return null;
        }

        private void store(Request request, HeadData data, List<String> keys) throws Exception {
            CompletableFuture<Void> write;
            synchronized (this) {
                // 强制查询接管同一键后, 较早的普通请求只完成自己的调用方.
                if (this.closed || request.result.isDone() || this.inFlight.get(request.query.key) != request) return;
                write = this.cache.put(keys, data);
            }
            write.get();
        }

        @Override
        public synchronized void close() {
            this.closed = true;
            for (Request request : List.copyOf(this.requests)) request.result.cancel(false);
            this.cache.clear();
            this.http.shutdownNow();
            this.executor.shutdownNow();
        }

        private final class Request {
            private final Query query;
            private final boolean force;
            private final CompletableFuture<HeadData> result = new CompletableFuture<>();
            private final FutureTask<Void> task;

            private Request(Query query, boolean force) {
                this.query = query;
                this.force = force;
                this.task = new FutureTask<>(() -> {
                    try {
                        this.result.complete(Session.this.lookup(this));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        this.result.cancel(false);
                    } catch (Exception exception) {
                        this.result.completeExceptionally(exception);
                    }
                    return null;
                });
            }
        }
    }
}
