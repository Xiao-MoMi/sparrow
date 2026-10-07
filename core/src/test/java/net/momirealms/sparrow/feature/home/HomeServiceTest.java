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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeServiceTest {
    private final UUID owner = UUID.randomUUID();
    private final HomeStore store = mock(HomeStore.class);
    private final PluginLogger logger = mock(PluginLogger.class);
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
    void invalidatesLazilyAndReloadsCurrentDataWithoutTouchingOtherOwners() {
        Home home = this.home("Home");
        when(this.store.loadByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture(List.of(home)));
        this.service.join(this.player());
        HomeSnapshot original = this.service.snapshot(this.owner).join();
        UUID otherOwner = UUID.randomUUID();
        SparrowPlayer other = mock(SparrowPlayer.class);
        when(other.uniqueId()).thenReturn(otherOwner);
        this.service.join(other);
        HomeSnapshot otherSnapshot = this.service.snapshot(otherOwner).join();
        this.service.accept(this.roundTrip(HomeChangedMessage.invalidateOwner("lobby", this.owner)));
        assertSame(original, this.service.snapshot(this.owner).join());
        UUID offline = UUID.randomUUID();
        this.service.accept(HomeChangedMessage.invalidateOwner("survival", offline));
        verify(this.store, never()).loadByOwner(offline);
        this.service.snapshot(offline).join();
        this.service.snapshot(offline).join();
        verify(this.store, times(2)).loadByOwner(offline);

        CompletableFuture<List<Home>> refresh = new CompletableFuture<>();
        when(this.store.loadByOwner(this.owner)).thenReturn(refresh);
        HomeChangedMessage notification = this.roundTrip(HomeChangedMessage.invalidateOwner("survival", this.owner));
        this.service.accept(notification);
        this.service.accept(notification);
        verify(this.store).loadByOwner(this.owner);
        assertSame(otherSnapshot, this.service.snapshot(otherOwner).join());
        var first = this.service.snapshot(this.owner);
        var second = this.service.snapshot(this.owner);
        verify(this.store, times(2)).loadByOwner(this.owner);
        Home replacement = this.home("MINE");
        refresh.complete(List.of(replacement));
        assertSame(first.join(), second.join());
        assertEquals(List.of(replacement), first.join().homes());
        assertEquals(home, original.get("home"));
        assertThrows(UnsupportedOperationException.class, () -> first.join().homes().clear());

        this.service.accept(this.roundTrip(HomeChangedMessage.invalidateAll("survival")));
        verify(this.store, times(2)).loadByOwner(this.owner);
        verify(this.store).loadByOwner(otherOwner);
        this.service.snapshot(this.owner).join();
        this.service.snapshot(otherOwner).join();
        verify(this.store, times(3)).loadByOwner(this.owner);
        verify(this.store, times(2)).loadByOwner(otherOwner);
        assertTrue(this.published.isEmpty());
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
        this.service.accept(HomeChangedMessage.invalidateOwner("survival", this.owner));
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
        this.service.accept(HomeChangedMessage.invalidateOwner("survival", this.owner));
        first.complete(List.of());
        second.complete(List.of(home));
        assertEquals(home, waiter.join().get("home"));
        this.service.accept(HomeChangedMessage.invalidateOwner("survival", this.owner));
        IllegalStateException failure = new IllegalStateException("database unavailable");
        when(this.store.loadByOwner(this.owner)).thenReturn(CompletableFuture.failedFuture(failure), CompletableFuture.completedFuture(List.of()));
        assertSame(failure, assertThrows(CompletionException.class, () -> this.service.snapshot(this.owner).join()).getCause());
        assertEquals(0, this.service.snapshot(this.owner).join().size());
        verify(this.logger).warn(contains("Failed to load homes"), same(failure));
    }

    @Test
    void publishesOnlyCommittedWritesAndInvalidatesLocalSnapshots() {
        this.service.join(this.player());
        Home home = this.home("Home");
        CompletableFuture<HomeStore.SaveResult> commit = new CompletableFuture<>();
        when(this.store.create(home)).thenReturn(commit);
        var result = this.service.create(home);
        HomeSnapshot empty = this.service.snapshot(this.owner).join();
        assertTrue(this.published.isEmpty());
        when(this.store.loadByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture(List.of(home)));
        commit.complete(new HomeStore.SaveResult(HomeStore.Status.SUCCESS, home));
        assertEquals(HomeStore.Status.SUCCESS, result.join().status());
        assertEquals(this.owner, this.roundTrip(this.published.getFirst()).owner());
        verify(this.store).loadByOwner(this.owner);
        assertEquals(0, empty.size());
        HomeSnapshot saved = this.service.snapshot(this.owner).join();
        assertEquals(home, saved.get("home"));
        when(this.store.update(home)).thenReturn(CompletableFuture.completedFuture(new HomeStore.SaveResult(HomeStore.Status.NOT_FOUND, null)), CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
        assertEquals(HomeStore.Status.NOT_FOUND, this.service.update(home).join().status());
        assertThrows(CompletionException.class, () -> this.service.update(home).join());
        assertSame(saved, this.service.snapshot(this.owner).join());
        assertEquals(1, this.published.size());
        when(this.store.delete(this.owner, home.id())).thenReturn(CompletableFuture.completedFuture(true), CompletableFuture.completedFuture(false));
        assertTrue(this.service.delete(this.owner, home.id()).join());
        assertFalse(this.service.delete(this.owner, home.id()).join());
        assertEquals(2, this.published.size());
        assertEquals(this.owner, this.published.getLast().owner());
        verify(this.store, times(2)).loadByOwner(this.owner);
        when(this.store.loadByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture(List.of()));
        assertEquals(0, this.service.snapshot(this.owner).join().size());
        HomeStore.Filter filter = new HomeStore.Filter(this.owner, null, null);
        when(this.store.deleteAll(filter)).thenReturn(CompletableFuture.completedFuture(0L));
        assertEquals(0, this.service.deleteAll(filter).join());
        assertEquals(this.owner, this.published.getLast().owner());
        verify(this.store, times(3)).loadByOwner(this.owner);
        this.service.snapshot(this.owner).join();
        verify(this.store, times(4)).loadByOwner(this.owner);
    }

    @Test
    void serializesQuotaChecksAndRejectsExistingHomesUntilDeleted() throws ReflectiveOperationException {
        HomeSettings settings = new HomeSettings();
        when(SparrowPlugin.instance().configurationManager().featuresConfig().config().home()).thenReturn(settings);
        when(this.store.findByName(eq(this.owner), anyString())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(this.store.countByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture(0L), CompletableFuture.completedFuture(1L));
        CompletableFuture<HomeStore.SaveResult> pending = new CompletableFuture<>();
        when(this.store.create(any())).thenReturn(pending);
        Home home = this.home("home");
        var first = this.service.set(this.owner, "home", home.server(), home.location(), 1);
        var second = this.service.set(this.owner, "mine", home.server(), home.location(), 1);
        verify(this.store, never()).findByName(this.owner, "mine");
        pending.complete(new HomeStore.SaveResult(HomeStore.Status.SUCCESS, home));
        assertEquals(HomeService.Status.CREATED, first.join().status());
        assertEquals(HomeService.Status.LIMIT_REACHED, second.join().status());
        when(this.store.findByName(this.owner, "home")).thenReturn(CompletableFuture.completedFuture(Optional.of(home)));
        assertEquals(HomeService.Status.DUPLICATE_NAME, this.service.set(this.owner, "home", "new", home.location(), 0).join().status());
        assertEquals(HomeService.Status.DUPLICATE_NAME, this.service.set(this.owner, "home", "new", home.location(), 3).join().status());
        verify(this.store, never()).update(any());
        verify(this.store).create(any());
        verify(this.store, times(2)).countByOwner(this.owner);
        assertEquals(HomeService.Status.LIMIT_REACHED, this.service.set(this.owner, "zero", "new", home.location(), 0).join().status());
        when(this.store.countByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture((long) Integer.MAX_VALUE));
        when(this.store.create(any())).thenAnswer(invocation -> CompletableFuture.completedFuture(new HomeStore.SaveResult(HomeStore.Status.SUCCESS, invocation.getArgument(0))));
        assertEquals(HomeService.Status.CREATED, this.service.set(this.owner, "all", "new", home.location(), Integer.MAX_VALUE).join().status());
        when(this.store.delete(this.owner, home.id())).thenReturn(CompletableFuture.completedFuture(true));
        assertTrue(this.service.delete(this.owner, "home").join());
        when(this.store.findByName(this.owner, "home")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(this.store.countByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture(0L));
        Home recreated = this.service.set(this.owner, "home", "new", home.location(), 3).join().home();
        assertNotEquals(home.id(), recreated.id());
        assertEquals("new", recreated.server());
        PluginTestContext.setField(settings, "namePattern", ".*");
        for (String invalid : List.of("a.b", "a b", "-flag", "a".repeat(33))) {
            assertEquals(HomeService.Status.INVALID_NAME, this.service.set(this.owner, invalid, "new", home.location(), 3).join().status());
            verify(this.store, never()).findByName(this.owner, invalid);
        }
    }

    @Test
    void editsCurrentRecordsWithinTheirOwnerAndOnlyInvalidatesSuccessfulWrites() {
        Home original = this.home("home");
        AtomicReference<Home> stored = new AtomicReference<>(original);
        when(this.store.find(original.id())).thenAnswer(invocation -> CompletableFuture.completedFuture(Optional.of(stored.get())));
        when(this.store.update(any())).thenAnswer(invocation -> {
            Home updated = invocation.getArgument(0);
            stored.set(updated);
            return CompletableFuture.completedFuture(new HomeStore.SaveResult(HomeStore.Status.SUCCESS, updated));
        });
        this.service.join(this.player());
        HomeSnapshot before = this.service.snapshot(this.owner).join();
        long startedAt = System.currentTimeMillis();
        HomeService.Result renamed = this.service.rename(this.owner, original.id(), "矿场").join();
        assertEquals(HomeService.Status.UPDATED, renamed.status());
        assertEquals(original.id(), renamed.home().id());
        assertEquals(original.owner(), renamed.home().owner());
        assertEquals(original.createdAt(), renamed.home().createdAt());
        assertEquals(original.location(), renamed.home().location());
        assertTrue(renamed.home().updatedAt() >= startedAt);
        assertNotSame(before, this.service.snapshot(this.owner).join());

        WorldLocation destination = new WorldLocation("nether", 3, 65, 4, 90, 0);
        HomeService.Result moved = this.service.relocate(this.owner, original.id(), "new-server", destination).join();
        assertEquals(HomeService.Status.UPDATED, moved.status());
        assertEquals("矿场", moved.home().name());
        assertEquals(original.createdAt(), moved.home().createdAt());
        assertEquals("new-server", moved.home().server());
        assertEquals(destination, moved.home().location());
        assertEquals(2, this.published.size());
        assertTrue(this.published.stream().allMatch(message -> this.owner.equals(message.owner())));

        doReturn(CompletableFuture.completedFuture(new HomeStore.SaveResult(HomeStore.Status.DUPLICATE_NAME, null))).when(this.store).update(any());
        HomeSnapshot current = this.service.snapshot(this.owner).join();
        assertEquals(HomeService.Status.DUPLICATE_NAME, this.service.rename(this.owner, original.id(), "occupied").join().status());
        assertSame(current, this.service.snapshot(this.owner).join());
        for (String invalid : List.of("a.b", "a b", "-flag", "a".repeat(33))) {
            assertEquals(HomeService.Status.INVALID_NAME, this.service.rename(this.owner, original.id(), invalid).join().status());
        }
        UUID stranger = UUID.randomUUID();
        assertEquals(HomeService.Status.NOT_FOUND, this.service.rename(stranger, original.id(), "stolen").join().status());
        assertEquals(HomeService.Status.NOT_FOUND, this.service.relocate(stranger, original.id(), "s", destination).join().status());
        when(this.store.find(original.id())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        assertEquals(HomeService.Status.NOT_FOUND, this.service.rename(this.owner, original.id(), "gone").join().status());
        when(this.store.find(original.id())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
        assertThrows(CompletionException.class, () -> this.service.relocate(this.owner, original.id(), "s", destination).join());
        verify(this.store, times(3)).update(any());
        verify(this.store, never()).create(any());
        verify(this.store, never()).countByOwner(any());
        assertEquals(2, this.published.size());
    }

    @Test
    void failedWritesReleaseTheQueueAndBulkInvalidationDiscardsPendingReads() {
        Home home = this.home("home");
        CompletableFuture<Optional<Home>> failed = new CompletableFuture<>();
        when(this.store.findByName(this.owner, "home")).thenReturn(failed, CompletableFuture.completedFuture(Optional.empty()));
        when(this.store.countByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture(0L));
        when(this.store.create(any())).thenAnswer(invocation -> CompletableFuture.completedFuture(new HomeStore.SaveResult(HomeStore.Status.SUCCESS, invocation.getArgument(0))));
        var first = this.service.set(this.owner, "home", "s", home.location(), 3);
        var second = this.service.set(this.owner, "home", "s", home.location(), 3);
        failed.completeExceptionally(new IllegalStateException("database unavailable"));
        assertThrows(CompletionException.class, first::join);
        assertEquals(HomeService.Status.CREATED, second.join().status());
        CompletableFuture<List<Home>> initial = new CompletableFuture<>();
        when(this.store.loadByOwner(this.owner)).thenReturn(initial, CompletableFuture.completedFuture(List.of()));
        this.service.join(this.player());
        var read = this.service.snapshot(this.owner);
        HomeStore.Filter filter = new HomeStore.Filter(null, null, "world");
        when(this.store.deleteAll(filter)).thenReturn(CompletableFuture.completedFuture(1L));
        assertEquals(1L, this.service.deleteAll(filter).join());
        assertNull(this.roundTrip(this.published.getLast()).owner());
        initial.complete(List.of(home));
        assertEquals(0, read.join().size());
        verify(this.store, times(2)).loadByOwner(this.owner);
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
            HomeChangedMessage.invalidateOwner("survival", this.owner).handle();
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
                when(this.store.loadByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture(List.of(home)));
                manager.publishOneWay(HomeChangedMessage.invalidateOwner("source", this.owner), "").get(5, TimeUnit.SECONDS);
                assertNotNull(received.poll(5, TimeUnit.SECONDS));
                verify(this.store).loadByOwner(this.owner);
                assertEquals(home, this.service.snapshot(this.owner).join().get("矿场"));
                when(this.store.loadByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture(List.of()));
                manager.publishOneWay(HomeChangedMessage.invalidateAll("source"), "").get(5, TimeUnit.SECONDS);
                assertNotNull(received.poll(5, TimeUnit.SECONDS));
                verify(this.store, times(2)).loadByOwner(this.owner);
                assertEquals(0, this.service.snapshot(this.owner).join().size());
                verify(this.store, times(3)).loadByOwner(this.owner);
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
            assertEquals(message.origin(), decoded.origin());
            assertEquals(message.owner(), decoded.owner());
            return decoded;
        } finally {
            buffer.release();
        }
    }
}
