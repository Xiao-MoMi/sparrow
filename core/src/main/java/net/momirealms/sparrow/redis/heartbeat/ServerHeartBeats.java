package net.momirealms.sparrow.redis.heartbeat;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public final class ServerHeartBeats {
    public static final String KEY_PREFIX = "sparrow:server:";  // 心跳键前缀, 后接服务器标识, 值为本次启动的 token
    private static final byte[] SERVERS_KEY = "sparrow:servers".getBytes(StandardCharsets.UTF_8); // 在线服务器索引, 成员为服务器标识, 分数为到期毫秒
    private static final long HEARTBEAT_INTERVAL_MILLIS = 2000; // 心跳续期周期, 存活期内可以错过两次续期
    private static final long HEARTBEAT_TTL_MILLIS = 6000;      // 心跳键存活期, 停跳超过它身份即消失
    private static final long PROBE_WAIT_MILLIS = 3000;         // 启动冲突探测的应答等待

    private static final String NOW_MILLIS = "local now = redis.call('TIME') local millis = tonumber(now[1]) * 1000 + math.floor(tonumber(now[2]) / 1000) ";
    private static final String HEARTBEAT_SCRIPT = NOW_MILLIS + "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2]) " + "redis.call('ZADD', KEYS[2], millis + tonumber(ARGV[2]), ARGV[3]) return 1";
    private static final String ONLINE_SCRIPT = NOW_MILLIS + "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. string.format('%d', millis)) " + "return redis.call('ZRANGE', KEYS[1], 0, -1)";
    private static final String SEIZE_SCRIPT = "local current = redis.call('GET', KEYS[1]); if not current or current == ARGV[1] then redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3]) return 1 else return 0 end";
    private static final String DELETE_SCRIPT = "if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('ZREM', KEYS[2], ARGV[2]) return redis.call('DEL', KEYS[1]) else return 0 end";

    private final SparrowPlugin plugin;
    private final String token; // 本次启动的身份凭据, 心跳键的值
    private String serverId;
    private byte[] key;
    private volatile SchedulerTask heartbeatTask;

    public ServerHeartBeats(@NotNull SparrowPlugin plugin) {
        this.plugin = plugin;
        this.token = UUID.randomUUID().toString();
    }

    public void onLoad() {
        this.serverId = ServerConfig.serverId();
        this.key = heartbeatKey(this.serverId);
        ServerProbeMessage.registry(this);
        if (!this.claimIdentity()) {
            this.plugin.logger().error(" ");
            this.plugin.logger().error("============================================================");
            this.plugin.logger().error(TranslationManager.console(LogConstants.SERVER_ID_DUPLICATE, this.serverId));
            this.plugin.logger().error("============================================================");
            this.plugin.logger().error(" ");
            throw new IllegalStateException("Server ID " + this.serverId + " is already used by another online server");
        }
        // 占到身份后立即登记, 加载完成时本服已出现在在线列表中
        this.plugin.redisConnector().connection().sync().eval(HEARTBEAT_SCRIPT, ScriptOutputType.INTEGER, this.heartbeatKeys(), this.heartbeatArguments());
        this.heartbeatTask = this.plugin.scheduler().asyncRepeating(this::heartbeat, HEARTBEAT_INTERVAL_MILLIS, HEARTBEAT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * 在 Redis 抢占一个身份, 如果发现同ID的服务器则进行心跳探测.
     * @return 是否抢占成功.
     */
    private boolean claimIdentity() {
        RedisCommands<byte[], byte[]> commands = this.plugin.redisConnector().connection().sync();
        byte[] existing = commands.setGet(this.key, this.token.getBytes(StandardCharsets.UTF_8), SetArgs.Builder.nx().px(HEARTBEAT_TTL_MILLIS));
        return existing == null || this.claimStaleIdentity(commands, existing);
    }

    // 同 id 的心跳键已存在就进行探测, 有应答 = 对方在线, 属配置冲突; 静默 = 残留身份.
    private boolean claimStaleIdentity(RedisCommands<byte[], byte[]> commands, byte[] observed) {
        if (this.probeHolder()) return false;
        // 残键可能在探测等待期间过期; 键已空或仍为观察值时原子接管, 其他 token 视为冲突.
        Long swapped = commands.eval(
                SEIZE_SCRIPT,
                ScriptOutputType.INTEGER,
                new byte[][]{this.key},
                observed,
                this.token.getBytes(StandardCharsets.UTF_8),
                Long.toString(HEARTBEAT_TTL_MILLIS).getBytes(StandardCharsets.UTF_8)
        );
        if (swapped == 0L) return false;
        this.plugin.logger().info(TranslationManager.console(LogConstants.SERVER_ID_SEIZED, new String(observed, StandardCharsets.UTF_8)));
        return true;
    }

    // 定向探测持有同 id 的服务器, 等出应答即在线.
    private boolean probeHolder() {
        try {
            this.plugin.messageBrokerManager().broker().publishTwoWay(new ServerProbeMessage(this.token), this.serverId)
                    .orTimeout(PROBE_WAIT_MILLIS, TimeUnit.MILLISECONDS)
                    .join();
            return true;
        } catch (CompletionException exception) {
            return false;
        }
    }

    // 回答一次身份探测, 自己发出的探测不应答.
    @Nullable
    ServerProbeResponseMessage answerProbe(@NotNull String requesterToken) {
        return this.token.equals(requesterToken) ? null : new ServerProbeResponseMessage(this.token);
    }

    /**
     * 进行一次心跳, 键和在线索引在同一个脚本里续期
     */
    private void heartbeat() {
        this.plugin.redisConnector().connection().async().eval(HEARTBEAT_SCRIPT, ScriptOutputType.INTEGER, this.heartbeatKeys(), this.heartbeatArguments());
    }

    private byte[][] heartbeatKeys() {
        return new byte[][]{this.key, SERVERS_KEY};
    }

    private byte[][] heartbeatArguments() {
        return new byte[][]{
                this.token.getBytes(StandardCharsets.UTF_8),
                Long.toString(HEARTBEAT_TTL_MILLIS).getBytes(StandardCharsets.UTF_8),
                this.serverId.getBytes(StandardCharsets.UTF_8)
        };
    }

    /**
     * 查询指定服务器的心跳键是否仍然存在.
     *
     * @param serverId 服务器标识
     * @return 查询任务, 服务器在线时结果为 {@code true}; Redis 出错时异常完成
     */
    @NotNull
    public CompletableFuture<Boolean> isOnline(@NotNull String serverId) {
        return this.plugin.redisConnector().connection().async().exists(heartbeatKey(serverId)).toCompletableFuture().thenApply(count -> count > 0);
    }

    /**
     * 查询仍在续期心跳的服务器标识, 结果包含本服. 查询时顺带清理在线索引中已经到期的服务器.
     *
     * @return 查询任务, 结果为无固定顺序的服务器标识; Redis 出错时异常完成
     */
    @NotNull
    public CompletableFuture<Set<String>> onlineServers() {
        return this.plugin.redisConnector().connection().async().<List<Object>>eval(ONLINE_SCRIPT, ScriptOutputType.MULTI, new byte[][]{SERVERS_KEY})
                .toCompletableFuture()
                .thenApply(members -> {
                    Set<String> servers = new HashSet<>();
                    int size = members.size();
                    for (int i = 0; i < size; i++) {
                        servers.add(new String((byte[]) members.get(i), StandardCharsets.UTF_8));
                    }
                    return Collections.unmodifiableSet(servers);
                });
    }

    @NotNull
    public String serverId() {
        return this.serverId;
    }

    public void shutdown() {
        SchedulerTask task = this.heartbeatTask;
        if (task == null) return;
        task.cancel();
        // 只注销仍属于本次启动的身份, 已被接管的身份保持原样.
        this.plugin.redisConnector().connection().async().eval(DELETE_SCRIPT, ScriptOutputType.INTEGER, this.heartbeatKeys(), this.token.getBytes(StandardCharsets.UTF_8), this.serverId.getBytes(StandardCharsets.UTF_8));
    }


    /**
     * 返回指定服务器的心跳键.
     *
     * @param serverId 服务器标识
     * @return UTF-8 编码的 Redis 键
     */
    public static byte @NotNull [] heartbeatKey(@NotNull String serverId) {
        return (KEY_PREFIX + serverId).getBytes(StandardCharsets.UTF_8);
    }
}
