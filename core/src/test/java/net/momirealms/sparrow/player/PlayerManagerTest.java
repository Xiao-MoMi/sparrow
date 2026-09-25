package net.momirealms.sparrow.player;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import net.momirealms.sparrow.player.cluster.ClusterRoster;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerManagerTest {
    @Test
    void concurrentCloseNotificationsRemoveThePlayerOnlyOnce() throws Exception {
        try (var rosters = mockConstruction(ClusterRoster.class); var executor = Executors.newFixedThreadPool(4)) {
            Fixture fixture = new Fixture();
            CompletableFuture<?>[] closes = new CompletableFuture<?>[100];
            for (int i = 0; i < closes.length; i++) {
                closes[i] = CompletableFuture.runAsync(() -> fixture.manager.operationComplete(fixture.close), executor);
            }
            CompletableFuture.allOf(closes).join();
            assertNull(fixture.manager.getPlayer(fixture.id));
            assertNull(fixture.manager.getConnection(fixture.channel));
            verify(rosters.constructed().getFirst()).presence(fixture.id, "Tester", false);
            verify(fixture.close).removeListener(fixture.manager);
        }
    }

    @Test
    void closingAnOldConnectionKeepsTheReconnectedPlayer() throws Exception {
        try (var rosters = mockConstruction(ClusterRoster.class)) {
            Fixture fixture = new Fixture();
            BukkitSparrowPlayer replacement = mock(BukkitSparrowPlayer.class);
            when(replacement.connection()).thenReturn(mock(PlayerConnection.class));
            fixture.players.put(fixture.id, replacement);
            fixture.manager.operationComplete(fixture.close);
            assertSame(replacement, fixture.manager.getPlayer(UUID.fromString(fixture.id.toString())));
            assertNull(fixture.manager.getConnection(fixture.channel));
            verifyNoInteractions(rosters.constructed().getFirst());
        }
    }

    @Test
    void onlinePlayersAreAnImmutableSnapshot() throws Exception {
        try (var ignored = mockConstruction(ClusterRoster.class)) {
            Fixture fixture = new Fixture();
            Collection<SparrowPlayer> snapshot = fixture.manager.getOnlinePlayers();
            fixture.manager.operationComplete(fixture.close);
            assertEquals(1, snapshot.size());
            assertSame(fixture.player, snapshot.iterator().next());
            assertThrows(UnsupportedOperationException.class, snapshot::clear);
            assertTrue(fixture.manager.getOnlinePlayers().isEmpty());
        }
    }

    private static final class Fixture {
        private final PlayerManager manager = new PlayerManager(mock(SparrowPlugin.class));
        private final UUID id = UUID.randomUUID();
        private final Channel channel = mock(Channel.class);
        private final ChannelFuture close = mock(ChannelFuture.class);
        private final BukkitSparrowPlayer player = mock(BukkitSparrowPlayer.class);
        private final ConcurrentChainedObject2ObjectHashTable<UUID, BukkitSparrowPlayer> players;

        @SuppressWarnings("unchecked")
        private Fixture() throws Exception {
            Field playersField = PlayerManager.class.getDeclaredField("players");
            playersField.setAccessible(true);
            this.players = (ConcurrentChainedObject2ObjectHashTable<UUID, BukkitSparrowPlayer>) playersField.get(this.manager);
            Field connectionsField = PlayerManager.class.getDeclaredField("connections");
            connectionsField.setAccessible(true);
            var connections = (ConcurrentChainedObject2ObjectHashTable<Channel, PlayerConnection>) connectionsField.get(this.manager);
            PlayerConnection connection = mock(PlayerConnection.class);
            when(connection.uniqueId()).thenReturn(this.id);
            when(connection.name()).thenReturn("Tester");
            when(this.player.connection()).thenReturn(connection);
            when(this.channel.closeFuture()).thenReturn(this.close);
            when(this.close.channel()).thenReturn(this.channel);
            connections.put(this.channel, connection);
            this.players.put(this.id, this.player);
        }
    }
}
