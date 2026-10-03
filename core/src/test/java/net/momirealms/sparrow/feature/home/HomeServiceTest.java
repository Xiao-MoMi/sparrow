package net.momirealms.sparrow.feature.home;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.redis.MessageBrokerManager;
import net.momirealms.sparrow.redis.RedisConnector;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.Logger;
import net.momirealms.sparrow.util.WorldLocation;
import net.momirealms.sparrow.testutil.PluginTestContext;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeServiceTest {
    private final UUID owner = UUID.randomUUID();
    private final HomeStore store = mock(HomeStore.class);
    private final PluginLogger logger = mock(PluginLogger.class);
    private final AtomicLong clock = new AtomicLong();
    private final List<HomeChangedMessage> published = new ArrayList<>();
    private HomeService service;
    private final MessageBrokerManager broker = mock(MessageBrokerManager.class);
    private PluginTestContext context;

    @BeforeEach
    void configure() throws ReflectiveOperationException {
        SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.messageBrokerManager()).thenReturn(this.broker);
        when(plugin.dataStorage().homeStore()).thenReturn(this.store);
        when(plugin.logger()).thenReturn(this.logger);
        when(plugin.configurationManager().featuresConfig().config().home()).thenReturn(new HomeSettings());
        this.context = new PluginTestContext(plugin, "lobby");
        this.service = new HomeService();
        PluginTestContext.setField(this.service, "clock", (LongSupplier) this.clock::get);
        when(this.broker.publishOneWay(any(HomeChangedMessage.class), eq(""))).thenAnswer(invocation -> {
            this.published.add(invocation.getArgument(0));
            return CompletableFuture.completedFuture(1L);
        });
        when(this.store.loadByOwner(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
    }

    @AfterEach
    void close() throws IllegalAccessException {
        this.service.close();
        HomeChangedMessage.listener(null);
        this.context.close();
    }

    @Test
    void reusesEmptySnapshotsAndExpiresFromReadStartWithoutExtendingOnReadsOrMessages() {
        CompletableFuture<List<Home>> initial = new CompletableFuture<>();
        CompletableFuture<List<Home>> refresh = new CompletableFuture<>();
        when(this.store.loadByOwner(this.owner)).thenReturn(initial, refresh);
        this.service.join(this.player());
        CompletableFuture<HomeSnapshot> waiting = this.service.snapshot(this.owner);
        this.clock.set(TimeUnit.SECONDS.toNanos(100));
        initial.complete(List.of());
        HomeSnapshot empty = waiting.join();
        assertSame(empty, this.service.snapshot(this.owner).join());
        this.clock.set(TimeUnit.SECONDS.toNanos(299));
        Home home = this.home("Home");
        this.service.accept(HomeChangedMessage.save("survival", home));
        assertEquals(home, this.service.snapshot(this.owner).join().get("HOME"));
        verify(this.store).loadByOwner(this.owner);
        this.clock.set(TimeUnit.SECONDS.toNanos(300));
        assertEquals(List.of("Home"), this.service.complete(this.owner, "h", 10));
        CompletableFuture<HomeSnapshot> refreshed = this.service.snapshot(this.owner);
        assertFalse(refreshed.isDone());
        verify(this.store, times(2)).loadByOwner(this.owner);
        refresh.complete(List.of());
        assertEquals(0, refreshed.join().size());
    }

    @Test
    void appliesCompleteMessagesInArrivalOrderAndDeletesOnlyTheOriginalUuid() {
        this.service.join(this.player());
        Home home = this.home("İ".repeat(32));
        this.service.accept(this.roundTrip(HomeChangedMessage.save("survival", home)));
        HomeSnapshot original = this.service.snapshot(this.owner).join();
        assertEquals(home, original.get(home.name()));
        Home renamed = new Home(home.id(), home.owner(), "Mine", "other", home.location(), home.createdAt(), 1);
        this.service.accept(this.roundTrip(HomeChangedMessage.save("survival", renamed)));
        Home replacement = this.home("MINE");
        this.service.accept(this.roundTrip(HomeChangedMessage.save("survival", replacement)));
        this.service.accept(this.roundTrip(HomeChangedMessage.delete("survival", new HomeStore.DeleteResult(this.owner, home.id(), home.key()))));
        HomeSnapshot snapshot = this.service.snapshot(this.owner).join();
        assertEquals(List.of(replacement), snapshot.homes());
        assertEquals(home, original.get(home.name()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.homes().clear());
        this.service.accept(HomeChangedMessage.save("lobby", home));
        Home offline = new Home(UUID.randomUUID(), UUID.randomUUID(), "Offline", "other", home.location(), 0, 0);
        this.service.accept(HomeChangedMessage.save("survival", offline));
        verify(this.store, never()).loadByOwner(offline.owner());
        this.service.snapshot(offline.owner()).join();
        this.service.snapshot(offline.owner()).join();
        verify(this.store, times(2)).loadByOwner(offline.owner());
        assertTrue(this.published.isEmpty());
        this.service.accept(this.roundTrip(HomeChangedMessage.invalidateOwner("survival", this.owner)));
        verify(this.store).loadByOwner(this.owner);
        assertEquals(0, this.service.snapshot(this.owner).join().size());
        verify(this.store, times(2)).loadByOwner(this.owner);
    }

    @Test
    void coalescesChangesDuringLoadAndIsolatesOldPlayersAndCancelledWaiters() {
        CompletableFuture<List<Home>> first = new CompletableFuture<>();
        CompletableFuture<List<Home>> reread = new CompletableFuture<>();
        CompletableFuture<List<Home>> rejoin = new CompletableFuture<>();
        when(this.store.loadByOwner(this.owner)).thenReturn(first, reread, rejoin);
        SparrowPlayer oldPlayer = this.player();
        this.service.join(oldPlayer);
        CompletableFuture<HomeSnapshot> waiter = this.service.snapshot(this.owner);
        this.service.snapshot(this.owner).cancel(false);
        Home changed = this.home("Mine");
        this.service.accept(HomeChangedMessage.save("survival", changed));
        this.service.accept(HomeChangedMessage.invalidateOwner("survival", this.owner));
        first.complete(List.of());
        assertFalse(waiter.isDone());
        verify(this.store, times(2)).loadByOwner(this.owner);
        SparrowPlayer newPlayer = this.player();
        this.service.join(newPlayer);
        assertInstanceOf(CancellationException.class, assertThrows(CompletionException.class, waiter::join).getCause());
        this.service.quit(oldPlayer);
        CompletableFuture<HomeSnapshot> newWaiter = this.service.snapshot(this.owner);
        reread.complete(List.of(changed));
        assertFalse(newWaiter.isDone());
        rejoin.complete(List.of());
        assertEquals(0, newWaiter.join().size());
        verify(this.store, times(3)).loadByOwner(this.owner);
        this.service.quit(newPlayer);
        assertTrue(this.service.complete(this.owner, "", 10).isEmpty());
    }

    @Test
    void rereadsAfterLoadChangesAndPropagatesDatabaseFailuresSoTheyCanBeRetried() {
        CompletableFuture<List<Home>> first = new CompletableFuture<>();
        CompletableFuture<List<Home>> second = new CompletableFuture<>();
        when(this.store.loadByOwner(this.owner)).thenReturn(first, second);
        this.service.join(this.player());
        CompletableFuture<HomeSnapshot> waiter = this.service.snapshot(this.owner);
        Home home = this.home("Home");
        this.service.accept(HomeChangedMessage.save("survival", home));
        first.complete(List.of());
        second.complete(List.of(home));
        assertEquals(home, waiter.join().get("home"));
        this.clock.set(TimeUnit.SECONDS.toNanos(301));
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(this.store.loadByOwner(this.owner)).thenReturn(CompletableFuture.failedFuture(failure), CompletableFuture.completedFuture(List.of()));
        assertSame(failure, assertThrows(CompletionException.class, () -> this.service.snapshot(this.owner).join()).getCause());
        assertEquals(0, this.service.snapshot(this.owner).join().size());
        verify(this.logger).warn(contains("Failed to load homes"), same(failure));
    }

    @Test
    void publishesOnlyCommittedWritesAndKeepsSuccessWhenPublishingFails() {
        this.service.join(this.player());
        Home home = this.home("Home");
        CompletableFuture<HomeStore.SaveResult> commit = new CompletableFuture<>();
        when(this.store.create(home)).thenReturn(commit);
        CompletableFuture<HomeStore.SaveResult> result = this.service.create(home);
        assertTrue(this.published.isEmpty());
        assertEquals(0, this.service.snapshot(this.owner).join().size());
        commit.complete(new HomeStore.SaveResult(HomeStore.Status.SUCCESS, home));
        assertEquals(HomeStore.Status.SUCCESS, result.join().status());
        assertEquals(home, this.published.getFirst().home());
        assertEquals(home, this.service.snapshot(this.owner).join().get("home"));
        when(this.store.update(home)).thenReturn(CompletableFuture.completedFuture(new HomeStore.SaveResult(HomeStore.Status.NOT_FOUND, null)), CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
        assertEquals(HomeStore.Status.NOT_FOUND, this.service.update(home).join().status());
        assertThrows(CompletionException.class, () -> this.service.update(home).join());
        assertEquals(1, this.published.size());
        HomeStore.DeleteResult deleted = new HomeStore.DeleteResult(this.owner, home.id(), home.key());
        when(this.store.delete(this.owner, home.id())).thenReturn(CompletableFuture.completedFuture(Optional.of(deleted)));
        this.service.delete(this.owner, home.id()).join();
        assertEquals(0, this.service.snapshot(this.owner).join().size());
        assertEquals(deleted, this.published.getLast().deleted());
        when(this.store.deleteByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture(0L));
        assertEquals(0, this.service.deleteByOwner(this.owner).join());
        assertEquals(HomeChangedMessage.Type.INVALIDATE_OWNER, this.published.getLast().type());
        this.service.snapshot(this.owner).join();
        verify(this.store, times(2)).loadByOwner(this.owner);

        CompletableFuture<Long> publication = new CompletableFuture<>();
        IllegalStateException unavailable = new IllegalStateException("redis unavailable");
        when(this.broker.publishOneWay(any(HomeChangedMessage.class), eq(""))).thenReturn(publication);
        assertEquals(HomeStore.Status.SUCCESS, this.service.create(home).join().status());
        publication.completeExceptionally(unavailable);
        assertEquals(home, this.service.snapshot(this.owner).join().get("home"));
        when(this.broker.publishOneWay(any(HomeChangedMessage.class), eq(""))).thenThrow(unavailable);
        assertEquals(HomeStore.Status.SUCCESS, this.service.create(home).join().status());
        verify(this.logger, times(2)).warn(contains("could not be published"), same(unavailable));
    }

    @Test
    void hotDisableCancelsLoadsAndOldScheduledJoinsCannotRefillTheNewService() {
        SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.configurationManager().featuresConfig().config().home()).thenReturn(new HomeSettings());
        when(plugin.dataStorage().homeStore()).thenReturn(this.store);
        when(this.store.initialize()).thenReturn(CompletableFuture.completedFuture(null));
        SparrowPlayer player = this.player();
        when(player.platformPlayer()).thenReturn(mock(Player.class));
        when(plugin.playerManager().getOnlinePlayers()).thenReturn(List.of(player));
        when(plugin.playerManager().getPlayer(this.owner)).thenReturn(player);
        List<Runnable> scheduled = new ArrayList<>();
        PlatformExecutor executor = plugin.scheduler().platform();
        doAnswer(invocation -> { scheduled.add(invocation.getArgument(0)); return null; })
                .when(executor).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
        CompletableFuture<List<Home>> pending = new CompletableFuture<>();
        when(this.store.loadByOwner(this.owner)).thenReturn(pending, CompletableFuture.completedFuture(List.of()));
        HomeFeature feature = new HomeFeature(plugin);
        try (var config = mockStatic(ServerConfig.class)) {
            config.when(ServerConfig::serverId).thenReturn("lobby");
            feature.loadConfig();
            feature.onLoad();
            verify(plugin.playerManager()).registerListener(feature);
            feature.onEnable();
            HomeService old = feature.service();
            feature.onJoin(player);
            CompletableFuture<HomeSnapshot> waiting = old.snapshot(this.owner);
            feature.onDisable();
            assertInstanceOf(CancellationException.class, assertThrows(CompletionException.class, waiting::join).getCause());
            feature.onEnable();
            scheduled.getFirst().run();
            verify(this.store).loadByOwner(this.owner);
            scheduled.getLast().run();
            pending.complete(List.of(this.home("Old")));
            assertEquals(0, feature.service().snapshot(this.owner).join().size());
            verify(this.store, times(2)).loadByOwner(this.owner);
            feature.onDisable();
            HomeChangedMessage.save("survival", this.home("Ignored")).handle();
            feature.onUnload();
            verify(plugin.playerManager()).unregisterListener(feature);
        } finally {
            feature.onDisable();
        }
    }

    @Test
    void synchronizesAcrossRealRedisUsingAnIsolatedChannel() throws Exception {
        RedisConnector first = new RedisConnector(new PluginConfig.RedisOptions(), this.logger);
        RedisConnector second = new RedisConnector(new PluginConfig.RedisOptions(), this.logger);
        try (first; second) {
            try {
                first.initialize();
                second.initialize();
            } catch (RuntimeException unavailable) {
                Assumptions.abort("Local Redis test connection is unavailable");
            }
            byte[] channel = ("sparrow:test:home:" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
            MessageBroker<FriendlyByteBuf> source = MessageBroker.builder(FriendlyByteBuf::new).channel(channel).serverId("source").logger(mock(Logger.class)).connection(first.brokerConnection()).build();
            MessageBroker<FriendlyByteBuf> target = MessageBroker.builder(FriendlyByteBuf::new).channel(channel).serverId("lobby").logger(mock(Logger.class)).connection(second.brokerConnection()).build();
            source.registry().register(HomeChangedMessage.ID, HomeChangedMessage.CODEC);
            target.registry().register(HomeChangedMessage.ID, HomeChangedMessage.CODEC);
            SparrowPlugin plugin = mock(SparrowPlugin.class);
            when(plugin.redisConnector()).thenReturn(first);
            MessageBrokerManager manager = spy(new MessageBrokerManager(plugin));
            doReturn(source).when(manager).broker();
            LinkedBlockingQueue<HomeChangedMessage> received = new LinkedBlockingQueue<>();
            this.service.join(this.player());
            HomeChangedMessage.listener(message -> { this.service.accept(message); received.add(message); });
            target.subscribe();
            try {
                Home home = this.home("矿场");
                manager.publishOneWay(HomeChangedMessage.save("source", home), "").get(5, TimeUnit.SECONDS);
                assertNotNull(received.poll(5, TimeUnit.SECONDS));
                assertEquals(home, this.service.snapshot(this.owner).join().get("矿场"));
                manager.publishOneWay(HomeChangedMessage.delete("source", new HomeStore.DeleteResult(this.owner, home.id(), home.key())), "").get(5, TimeUnit.SECONDS);
                assertNotNull(received.poll(5, TimeUnit.SECONDS));
                assertEquals(0, this.service.snapshot(this.owner).join().size());
                manager.publishOneWay(HomeChangedMessage.invalidateOwner("source", this.owner), "").get(5, TimeUnit.SECONDS);
                assertNotNull(received.poll(5, TimeUnit.SECONDS));
                this.service.snapshot(this.owner).join();
                verify(this.store, times(2)).loadByOwner(this.owner);
                assertTrue(this.published.isEmpty());
            } finally {
                target.unsubscribe();
                HomeChangedMessage.listener(null);
            }
        }
    }

    private SparrowPlayer player() {
        SparrowPlayer player = mock(SparrowPlayer.class);
        when(player.uniqueId()).thenReturn(this.owner);
        return player;
    }

    private Home home(String name) {
        return new Home(UUID.randomUUID(), this.owner, name, "survival", new WorldLocation("world", -10.25, 63.5, 28.75, 125.5f, -40.5f), 10, 20);
    }

    private HomeChangedMessage roundTrip(HomeChangedMessage message) {
        message.setTargetServer("");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            HomeChangedMessage.CODEC.encode(buffer, message);
            HomeChangedMessage decoded = HomeChangedMessage.CODEC.decode(buffer);
            assertFalse(buffer.isReadable());
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
