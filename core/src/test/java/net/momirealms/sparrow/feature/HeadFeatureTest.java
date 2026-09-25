package net.momirealms.sparrow.feature;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.mojang.authlib.GameProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import org.bukkit.entity.Player;
import net.momirealms.sparrow.feature.head.HeadData;
import net.momirealms.sparrow.feature.head.HeadFeature;
import net.momirealms.sparrow.feature.head.HeadFetchException;
import net.momirealms.sparrow.feature.head.HeadSettings;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeadFeatureTest {
    private static final UUID ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void onlineProfileReadIsScheduledAndUntexturedOnlinePlayersFallBackToApi() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (Fixture fixture = new Fixture()) {
            set(fixture.settings, "sourceOrder", List.of("online", "api"));
            var online = mock(BukkitSparrowPlayer.class);
            var player = mock(Player.class);
            var handle = mock(ServerPlayer.class);
            when(online.name()).thenReturn("Tester");
            when(online.platformPlayer()).thenReturn(player);
            when(online.nmsPlayer()).thenReturn(handle);
            when(fixture.plugin.playerManager().getOnlinePlayers()).thenReturn(List.of(online));
            when(fixture.plugin.playerManager().getPlayer(player)).thenReturn(online);
            when(handle.getGameProfile()).thenReturn(new GameProfile(ID, "Tester"));
            var platform = fixture.plugin.scheduler().platform();
            var scheduled = new LinkedBlockingQueue<Runnable>();
            doAnswer(invocation -> {
                scheduled.add(invocation.getArgument(0));
                return null;
            }).when(platform).run(any(Runnable.class), any(Runnable.class), same(player));
            fixture.start();
            CompletableFuture<HeadData> lookup = fixture.head.fetchByName("Tester", false);
            Runnable read = scheduled.poll(2, TimeUnit.SECONDS);
            assertNotNull(read);
            assertFalse(lookup.isDone());
            verify(handle, never()).getGameProfile();
            read.run();
            assertEquals("old", lookup.get(2, TimeUnit.SECONDS).texture());
            assertEquals(1, fixture.profiles.get());
        }
    }

    @Test
    void individualHttpTimeoutFailsBeforeTheLongerTotalDeadline() throws Exception {
        try (Fixture fixture = new Fixture()) {
            set(fixture.settings.api(), "requestTimeout", "150ms");
            fixture.handler = exchange -> {
                await(new CountDownLatch(1));
                respond(exchange, 200, profile("late"));
            };
            fixture.start();
            ExecutionException error = assertThrows(ExecutionException.class, () -> fixture.head.fetchByUuid(ID, false).get(2, TimeUnit.SECONDS));
            assertInstanceOf(HttpTimeoutException.class, error.getCause());
        }
    }

    @Test
    void usesCustomEndpointsHeadersAndBothAliasesThenForceReplacesTheCachedValue() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.start();
            HeadData first = fixture.head.fetchByName("Tester", false).get(3, TimeUnit.SECONDS);
            assertEquals(ID, first.uuid());
            assertEquals(first, fixture.head.fetchByName("TESTER", false).get());
            assertEquals(first, fixture.head.fetchByUuid(ID, false).get());
            assertEquals(1, fixture.names.get());
            assertEquals(1, fixture.profiles.get());
            fixture.texture.set("new");
            HeadData fresh = fixture.head.fetchByUuid(ID, true).get();
            assertEquals("new", fresh.texture());
            assertEquals(fresh, fixture.head.fetchByName("Tester", false).get());
            assertEquals(2, fixture.profiles.get());
            assertEquals("local-test", fixture.header.get());
        }
    }

    @Test
    void forceDoesNotJoinAnOrdinaryRequestAndLateOrdinaryResultCannotOverwriteIt() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CountDownLatch waiting = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            fixture.handler = exchange -> {
                int request = fixture.profiles.incrementAndGet();
                if (request == 1) {
                    waiting.countDown();
                    await(release);
                }
                respond(exchange, 200, profile(request == 1 ? "old" : "new"));
            };
            fixture.start();
            CompletableFuture<HeadData> normal = fixture.head.fetchByUuid(ID, false);
            try {
                assertTrue(waiting.await(2, TimeUnit.SECONDS));
                HeadData forced = fixture.head.fetchByUuid(ID, true).get(2, TimeUnit.SECONDS);
                assertEquals("new", forced.texture());
            } finally {
                release.countDown();
            }
            assertEquals("old", normal.get(2, TimeUnit.SECONDS).texture());
            assertEquals("new", fixture.head.fetchByUuid(ID, false).get().texture());
            assertEquals(2, fixture.profiles.get());
        }
    }

    @Test
    void coalescesConcurrentForceRequestsAndCallerCancellationDoesNotCancelOtherCallers() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CountDownLatch waiting = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            fixture.handler = exchange -> {
                fixture.profiles.incrementAndGet();
                waiting.countDown();
                await(release);
                respond(exchange, 200, profile("shared"));
            };
            fixture.start();
            CompletableFuture<HeadData> first = fixture.head.fetchByUuid(ID, true);
            List<CompletableFuture<HeadData>> rest = new ArrayList<>();
            try {
                assertTrue(waiting.await(2, TimeUnit.SECONDS));
                for (int i = 0; i < 10; i++) rest.add(fixture.head.fetchByUuid(ID, true));
                first.cancel(false);
            } finally {
                release.countDown();
            }
            for (CompletableFuture<HeadData> future : rest) assertEquals("shared", future.get(2, TimeUnit.SECONDS).texture());
            assertEquals(1, fixture.profiles.get());
        }
    }

    @Test
    void totalTimeoutCompletesTheFutureAndNextRequestCanRetry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            set(fixture.settings, "requestTimeout", "150ms");
            fixture.handler = exchange -> {
                await(new CountDownLatch(1));
                respond(exchange, 200, profile("late"));
            };
            fixture.start();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> fixture.head.fetchByUuid(ID, false).get(2, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, failure.getCause());
            fixture.handler = exchange -> respond(exchange, 200, profile("retry"));
            assertEquals("retry", fixture.head.fetchByUuid(ID, false).get(2, TimeUnit.SECONDS).texture());
        }
    }

    @Test
    void stoppingAndReenablingInvalidatesOutstandingRequestsAndTheirCache() throws Exception {
        try (Fixture fixture = new Fixture()) {
            CountDownLatch waiting = new CountDownLatch(1);
            fixture.handler = exchange -> {
                waiting.countDown();
                await(new CountDownLatch(1));
                respond(exchange, 200, profile("late"));
            };
            fixture.start();
            long generation = fixture.head.generation();
            CompletableFuture<HeadData> old = fixture.head.fetchByUuid(ID, false);
            assertTrue(waiting.await(2, TimeUnit.SECONDS));
            ((Feature<?>) fixture.head).stop();
            assertTrue(old.isCompletedExceptionally());
            assertNotEquals(generation, fixture.head.generation());
            assertTrue(fixture.head.fetchByUuid(ID, false).isCompletedExceptionally());
            fixture.handler = exchange -> respond(exchange, 200, profile("enabled"));
            ((Feature<?>) fixture.head).start();
            assertEquals("enabled", fixture.head.fetchByUuid(ID, false).get(2, TimeUnit.SECONDS).texture());
        }
    }

    @Test
    void missingRateLimitedAndMalformedResponsesHaveDistinctOutcomesAndAreNotCached() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.start();
            fixture.handler = exchange -> respond(exchange, 404, "{}");
            assertNull(fixture.head.fetchByUuid(ID, false).get());
            fixture.handler = exchange -> respond(exchange, 429, "{}");
            ExecutionException rate = assertThrows(ExecutionException.class, () -> fixture.head.fetchByUuid(ID, false).get());
            assertEquals(HeadFetchException.Reason.THROTTLED, ((HeadFetchException) rate.getCause()).reason());
            fixture.handler = exchange -> respond(exchange, 200, "{broken");
            ExecutionException malformed = assertThrows(ExecutionException.class, () -> fixture.head.fetchByUuid(ID, false).get());
            assertEquals(HeadFetchException.Reason.INVALID_RESPONSE, ((HeadFetchException) malformed.getCause()).reason());
            fixture.handler = exchange -> respond(exchange, 200, profile("recovered"));
            assertEquals("recovered", fixture.head.fetchByUuid(ID, false).get().texture());
        }
    }

    @Test
    void urlsAreRejectedWithoutRequestingTheProfileService() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.start();
            for (String source : List.of("https://textures.minecraft.net/texture/abcd", "https://example.test/skin.png")) {
                ExecutionException error = assertThrows(ExecutionException.class, () -> fixture.head.fetchByName(source, false).get(2, TimeUnit.SECONDS));
                assertEquals(HeadFetchException.Reason.INVALID_INPUT, ((HeadFetchException) error.getCause()).reason());
            }
            assertEquals(0, fixture.names.get());
            assertEquals(0, fixture.profiles.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fallbackUsesOriginalNameOrUuidInOrderAndCachesTheSuccessfulProfile(boolean byName) throws Exception {
        try (Fixture fixture = new Fixture()) {
            List<String> requests = new ArrayList<>();
            AtomicReference<String> fallbackHeader = new AtomicReference<>();
            fixture.server.createContext("/missing/", exchange -> {
                requests.add(exchange.getRequestURI().getPath());
                respond(exchange, 404, "{}");
            });
            fixture.server.createContext("/broken/", exchange -> {
                requests.add(exchange.getRequestURI().getPath());
                respond(exchange, 200, "{broken");
            });
            fixture.server.createContext("/ashcon/", exchange -> {
                requests.add(exchange.getRequestURI().getPath());
                fallbackHeader.set(exchange.getRequestHeaders().getFirst("X-Test"));
                respond(exchange, 200, "{\"uuid\":\"" + ID + "\",\"username\":\"Tester\",\"textures\":{\"raw\":{\"value\":\"fallback\",\"signature\":\"signed\"}}}");
            });
            fixture.server.createContext("/unused/", exchange -> {
                requests.add("unused");
                respond(exchange, 500, "{}");
            });
            set(fixture.settings.api(), "fallbackUrls", List.of(fixture.base + "/missing/{player}", fixture.base + "/broken/{player}",
                    fixture.base + "/ashcon/{player}", fixture.base + "/unused/{player}"));
            fixture.start();
            assertEquals("old", fixture.head.fetchByUuid(ID, true).get().texture());
            assertTrue(requests.isEmpty());
            fixture.handler = exchange -> respond(exchange, 503, "{}");
            if (byName) {
                fixture.server.removeContext("/name/");
                fixture.server.createContext("/name/", exchange -> respond(exchange, 503, "{}"));
            }
            HeadData result = (byName ? fixture.head.fetchByName("Tester", true) : fixture.head.fetchByUuid(ID, true)).get(3, TimeUnit.SECONDS);
            String player = byName ? "Tester" : ID.toString();
            assertEquals(List.of("/missing/" + player, "/broken/" + player, "/ashcon/" + player), requests);
            assertEquals(new HeadData(ID, "Tester", "fallback", "signed"), result);
            assertNull(fallbackHeader.get());
            assertEquals(result, fixture.head.fetchByName("Tester", false).get());
            assertEquals(result, fixture.head.fetchByUuid(ID, false).get());
            assertEquals(3, requests.size());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 429, 503, 200})
    void missingTexturesAndHttpFailuresFallBackToMojangProfileFormat(int status) throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.handler = exchange -> respond(exchange, status, "{\"id\":\"" + ID + "\",\"name\":\"Tester\",\"properties\":[]}");
            fixture.server.createContext("/fallback/", exchange -> respond(exchange, 200, profile("fallback")));
            set(fixture.settings.api(), "fallbackUrls", List.of(fixture.base + "/fallback/{player}"));
            fixture.start();
            assertEquals("fallback", fixture.head.fetchByUuid(ID, false).get(3, TimeUnit.SECONDS).texture());
        }
    }

    @Test
    void individualHttpTimeoutCanFallBackButTotalTimeoutStopsTheChain() throws Exception {
        try (Fixture fixture = new Fixture()) {
            set(fixture.settings.api(), "requestTimeout", "200ms");
            fixture.handler = exchange -> await(new CountDownLatch(1));
            fixture.server.createContext("/fallback/", exchange -> respond(exchange, 200, profile("fallback")));
            set(fixture.settings.api(), "fallbackUrls", List.of(fixture.base + "/fallback/{player}"));
            fixture.start();
            assertEquals("fallback", fixture.head.fetchByUuid(ID, false).get(3, TimeUnit.SECONDS).texture());
        }
        try (Fixture fixture = new Fixture()) {
            set(fixture.settings, "requestTimeout", "300ms");
            AtomicInteger late = new AtomicInteger();
            fixture.handler = exchange -> respond(exchange, 503, "{}");
            fixture.server.createContext("/slow/", exchange -> await(new CountDownLatch(1)));
            fixture.server.createContext("/late/", exchange -> {
                late.incrementAndGet();
                respond(exchange, 200, profile("late"));
            });
            set(fixture.settings.api(), "fallbackUrls", List.of(fixture.base + "/slow/{player}", fixture.base + "/late/{player}"));
            fixture.start();
            ExecutionException error = assertThrows(ExecutionException.class, () -> fixture.head.fetchByUuid(ID, false).get(2, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, error.getCause());
            assertEquals(0, late.get());
        }
    }

    @Test
    void wrongUuidAndExhaustedFallbacksReportFailureAndAllowRetry() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.handler = exchange -> respond(exchange, 404, "{}");
            fixture.server.createContext("/wrong/", exchange -> respond(exchange, 200, profile("wrong").replace(ID.toString().replace("-", ""), UUID.randomUUID().toString())));
            fixture.server.createContext("/missing/", exchange -> respond(exchange, 404, "{}"));
            set(fixture.settings.api(), "fallbackUrls", List.of(fixture.base + "/wrong/{player}", fixture.base + "/missing/{player}"));
            fixture.start();
            ExecutionException error = assertThrows(ExecutionException.class, () -> fixture.head.fetchByUuid(ID, false).get());
            assertEquals(HeadFetchException.Reason.INVALID_RESPONSE, ((HeadFetchException) error.getCause()).reason());
            fixture.server.removeContext("/wrong/");
            fixture.server.createContext("/wrong/", exchange -> respond(exchange, 404, "{}"));
            assertNull(fixture.head.fetchByUuid(ID, false).get());
            fixture.handler = exchange -> respond(exchange, 200, profile("recovered"));
            assertEquals("recovered", fixture.head.fetchByUuid(ID, false).get().texture());
        }
    }

    private static String profile(String texture) {
        return "{\"id\":\"" + ID.toString().replace("-", "") + "\",\"name\":\"Tester\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + texture + "\"}]}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class Fixture implements AutoCloseable {
        private final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final HeadSettings settings = new HeadSettings();
        private final SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
        private final HeadFeature head = new HeadFeature(this.plugin);
        private final AtomicInteger names = new AtomicInteger();
        private final AtomicInteger profiles = new AtomicInteger();
        private final AtomicReference<String> texture = new AtomicReference<>("old");
        private final AtomicReference<String> header = new AtomicReference<>();
        private final String base = "http://127.0.0.1:" + this.server.getAddress().getPort();
        private volatile HttpHandler handler = exchange -> {
            this.profiles.incrementAndGet();
            this.header.set(exchange.getRequestHeaders().getFirst("X-Test"));
            respond(exchange, 200, profile(this.texture.get()));
        };

        private Fixture() throws Exception {
            set(this.settings, "sourceOrder", List.of("api"));
            set(this.settings.cache().redis(), "enabled", false);
            set(this.settings.api(), "nameUrl", this.base + "/name/{name}");
            set(this.settings.api(), "profileUrl", this.base + "/profile/{uuid}");
            set(this.settings.api(), "fallbackUrls", List.of());
            set(this.settings.api(), "headers", Map.of("X-Test", "local-test"));
            when(this.plugin.configurationManager().featuresConfig().config().head()).thenReturn(this.settings);
            this.server.setExecutor(this.executor);
            this.server.createContext("/name/", exchange -> {
                this.names.incrementAndGet();
                respond(exchange, 200, "{\"id\":\"" + ID + "\",\"name\":\"Tester\"}");
            });
            this.server.createContext("/profile/", exchange -> this.handler.handle(exchange));
            this.server.start();
        }

        private void start() {
            ((Feature<?>) this.head).install();
        }

        @Override
        public void close() {
            ((Feature<?>) this.head).uninstall();
            this.server.stop(0);
            this.executor.shutdownNow();
        }
    }
}
