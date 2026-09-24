package net.momirealms.sparrow.redis;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.redis.messagebroker.connection.RedisConnection;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageBrokerManagerTest {
    @Test
    void usesConfiguredIdentityAndDatabaseChannelAndUnsubscribesBeforeConnectionClose() {
        SparrowPlugin plugin = mock(SparrowPlugin.class);
        RedisConnector connector = mock(RedisConnector.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.isOpen()).thenReturn(true);
        when(plugin.redisConnector()).thenReturn(connector);
        when(plugin.logger()).thenReturn(mock(PluginLogger.class));
        when(connector.database()).thenReturn(3);
        when(connector.brokerConnection()).thenReturn(connection);
        try (MockedStatic<ServerConfig> config = mockStatic(ServerConfig.class)) {
            config.when(ServerConfig::serverId).thenReturn("server-a");
            MessageBrokerManager manager = new MessageBrokerManager(plugin);
            manager.onLoad();
            byte[] channel = "sparrow:db:3:messages".getBytes(StandardCharsets.UTF_8);
            assertArrayEquals(channel, manager.broker().channel());
            assertEquals("server-a", manager.broker().serverId());
            verify(connection).subscribe(eq(channel), any());
            manager.onDisable();
            manager.onDisable();
            verify(connection).unsubscribe(channel);
            verify(connection, never()).close();
            verify(connector, never()).close();
        }
    }
}
