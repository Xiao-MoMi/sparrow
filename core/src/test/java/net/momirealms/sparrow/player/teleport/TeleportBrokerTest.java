package net.momirealms.sparrow.player.teleport;

import net.momirealms.sparrow.world.WorldLocation;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.redis.MessageBrokerManager;
import net.momirealms.sparrow.redis.RedisConnector;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeleportBrokerTest {
    @Test
    void exchangesRequestAndResponseAndTimesOutMissingDestination() {
        PluginLogger logger = mock(PluginLogger.class);
        RedisConnector first = new RedisConnector(new PluginConfig.RedisOptions(), logger);
        RedisConnector second = new RedisConnector(new PluginConfig.RedisOptions(), logger);
        try {
            first.initialize();
            second.initialize();
        } catch (RuntimeException unavailable) {
            first.close();
            second.close();
            Assumptions.abort("Local Redis test connection is unavailable");
        }
        String sourceId = "test-" + UUID.randomUUID();
        String targetId = "test-" + UUID.randomUUID();
        MessageBrokerManager source = this.start(first, sourceId, logger);
        MessageBrokerManager target = this.start(second, targetId, logger);
        TeleportManager destination = mock(TeleportManager.class);
        TeleportRequest.manager(destination);
        UUID player = UUID.randomUUID();
        WorldLocation location = new WorldLocation("world", -38.25, 64.5, 55.75, 173.25f, -43.5f);
        try {
            when(destination.prepare(player, location)).thenReturn(true);
            var response = source.broker().publishTwoWay(new TeleportRequest(player, location), targetId).orTimeout(5, TimeUnit.SECONDS).join();
            assertTrue(response.accepted());
            verify(destination).prepare(player, location);
            when(destination.prepare(player, location)).thenReturn(false);
            assertFalse(source.broker().publishTwoWay(new TeleportRequest(player, location), targetId).orTimeout(5, TimeUnit.SECONDS).join().accepted());
            CompletionException timeout = assertThrows(CompletionException.class,
                    () -> source.broker().publishTwoWay(new TeleportRequest(player, location), "test-missing-" + UUID.randomUUID()).orTimeout(5, TimeUnit.SECONDS).join());
            assertInstanceOf(TimeoutException.class, timeout.getCause());
        } finally {
            TeleportRequest.manager(null);
            target.onDisable();
            source.onDisable();
            second.close();
            first.close();
        }
    }

    private MessageBrokerManager start(RedisConnector connector, String server, PluginLogger logger) {
        SparrowPlugin plugin = mock(SparrowPlugin.class);
        when(plugin.redisConnector()).thenReturn(connector);
        when(plugin.logger()).thenReturn(logger);
        MessageBrokerManager manager = new MessageBrokerManager(plugin);
        try (var config = mockStatic(ServerConfig.class)) {
            config.when(ServerConfig::serverId).thenReturn(server);
            manager.onLoad();
        }
        return manager;
    }
}
