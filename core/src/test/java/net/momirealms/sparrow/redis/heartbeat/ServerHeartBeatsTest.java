package net.momirealms.sparrow.redis.heartbeat;

import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.plugin.scheduler.SchedulerAdapter;
import net.momirealms.sparrow.plugin.scheduler.task.AsyncTask;
import net.momirealms.sparrow.redis.MessageBrokerManager;
import net.momirealms.sparrow.redis.RedisConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ServerHeartBeatsTest {
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final List<Runnable> cleanups = new ArrayList<>();
    private RedisConnector connector; // 最近启动的服务器使用的连接

    @AfterEach
    void close() {
        for (int i = this.cleanups.size() - 1; i >= 0; i--) {
            this.cleanups.get(i).run();
        }
        this.executor.shutdownNow();
    }

    @Test
    void publishesOnlineServersAndRemovesKeyOnShutdown() throws Exception {
        String prefix = "test-" + UUID.randomUUID() + "-";
        ServerHeartBeats first = this.start(prefix + "a");
        ServerHeartBeats second = this.start(prefix + "b");
        // 在线列表包含两台服务器, 单台查询与之一致
        assertTrue(first.onlineServers().join().containsAll(List.of(prefix + "a", prefix + "b")));
        assertTrue(second.isOnline(prefix + "a").join());
        // 心跳续期后键仍然存在
        Thread.sleep(3500);
        assertTrue(first.isOnline(prefix + "b").join());
        // 注销后心跳键消失
        second.shutdown();
        Thread.sleep(200);
        assertFalse(first.isOnline(prefix + "b").join());
        assertFalse(first.onlineServers().join().contains(prefix + "b"));
    }

    @Test
    void seizesStaleIdentityWithoutResponder() throws Exception {
        String serverId = "test-" + UUID.randomUUID();
        ServerHeartBeats probe = this.start("test-" + UUID.randomUUID());
        // 模拟上次崩溃留下的心跳键, 没有服务器应答探测
        this.connector.connection().sync().set(ServerHeartBeats.heartbeatKey(serverId), "stale".getBytes());
        ServerHeartBeats seized = this.start(serverId);
        assertTrue(probe.isOnline(serverId).join());
        assertNotEquals("stale", new String(this.connector.connection().sync().get(ServerHeartBeats.heartbeatKey(serverId))));
        seized.shutdown();
    }

    @Test
    void dropsExpiredServersFromIndex() throws Exception {
        ServerHeartBeats heartBeats = this.start("test-" + UUID.randomUUID());
        String ghost = "test-ghost-" + UUID.randomUUID();
        byte[] index = "sparrow:servers".getBytes();
        // 模拟崩溃后停止续期的服务器, 到期时间已经过去
        this.connector.connection().sync().zadd(index, 1, ghost.getBytes());
        assertFalse(heartBeats.onlineServers().join().contains(ghost));
        assertNull(this.connector.connection().sync().zscore(index, ghost.getBytes()));
    }

    private ServerHeartBeats start(String serverId) throws Exception {
        PluginConfig.RedisOptions options = new PluginConfig.RedisOptions();
        set(options, "url", "redis://localhost:6379/15");
        RedisConnector connector = new RedisConnector(options);
        try {
            connector.initialize();
        } catch (RuntimeException exception) {
            Assumptions.abort("local Redis is not reachable: " + exception.getMessage());
        }
        this.cleanups.add(connector::close);
        this.connector = connector;

        SparrowPlugin plugin = mock(SparrowPlugin.class);
        SchedulerAdapter scheduler = mock(SchedulerAdapter.class);
        when(scheduler.asyncRepeating(any(Runnable.class), anyLong(), anyLong(), any())).thenAnswer(invocation -> new AsyncTask(
                this.executor.scheduleAtFixedRate(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3))));
        MessageBrokerManager brokerManager = new MessageBrokerManager(plugin);
        ServerHeartBeats heartBeats = new ServerHeartBeats(plugin);
        when(plugin.redisConnector()).thenReturn(connector);
        when(plugin.logger()).thenReturn(mock(PluginLogger.class));
        when(plugin.scheduler()).thenReturn(scheduler);
        when(plugin.messageBrokerManager()).thenReturn(brokerManager);
        try (MockedStatic<ServerConfig> config = mockStatic(ServerConfig.class);
             MockedStatic<TranslationManager> translations = mockStatic(TranslationManager.class)) {
            config.when(ServerConfig::serverId).thenReturn(serverId);
            translations.when(() -> TranslationManager.console(anyString(), any(String[].class))).thenReturn("");
            brokerManager.onLoad();
            this.cleanups.add(brokerManager::onDisable);
            heartBeats.onLoad();
        }
        this.cleanups.add(heartBeats::shutdown);
        return heartBeats;
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
