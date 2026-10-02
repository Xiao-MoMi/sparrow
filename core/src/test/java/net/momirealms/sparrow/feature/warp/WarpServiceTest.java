package net.momirealms.sparrow.feature.warp;

import net.momirealms.sparrow.database.WarpStore;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.util.WorldLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

import static net.momirealms.sparrow.feature.warp.WarpService.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarpServiceTest {
    private final InMemoryWarpStore store = spy(new InMemoryWarpStore());
    private final WarpRegistry registry = new WarpRegistry(this.store, "lobby");
    private final WarpSettings settings = mock(WarpSettings.class);
    private final PluginLogger logger = mock(PluginLogger.class);
    private final List<WarpMessage> published = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong(100);
    private final WorldLocation location = new WorldLocation("world", 1, 64, 2, 90, 0);
    private final WarpService service = new WarpService(this.store, this.registry, "lobby", this.published::add, this.logger, () -> this.settings, this.clock::get);

    @BeforeEach
    void configure() {
        when(this.settings.namePattern()).thenReturn("[\\p{L}\\p{N}_][\\p{L}\\p{N}_-]*");
        when(this.settings.overwriteExisting()).thenReturn(true);
    }

    @Test
    void createsAndOverwritesUsingDatabaseStateAndServiceTimestamps() {
        UUID creator = UUID.randomUUID();
        WarpService.Result created = this.service.set("Spawn", "lobby", this.location, creator).join();
        assertEquals(CREATED, created.status());
        Warp first = created.warp();
        assertEquals(7, first.id().version());
        assertEquals(100, first.createdAt());
        assertEquals(100, first.updatedAt());
        assertEquals(creator, first.creator());
        assertSame(first, this.registry.get("spawn"));
        assertSame(first, this.published.getFirst().warp());

        Warp remote = new Warp(first.id(), "Spawn", "remote description", "other", this.location, creator, 100, 200);
        this.store.put(remote);
        this.clock.set(300);
        WarpService.Result result = this.service.set("SPAWN", "new_server", this.location, UUID.randomUUID()).join();
        assertEquals(UPDATED, result.status());
        Warp saved = result.warp();
        assertEquals(first.id(), saved.id());
        assertEquals(100, saved.createdAt());
        assertEquals(300, saved.updatedAt());
        assertEquals(creator, saved.creator());
        assertEquals("remote description", saved.description());
        assertEquals("new_server", saved.server());
        assertEquals(saved, this.registry.get(first.id()));
        assertEquals(saved, this.published.getLast().warp());
    }

    @Test
    void editingReadsLatestFieldsAndPreservesConsoleCreation() {
        Warp initial = this.service.set("Spawn", "lobby", this.location, null).join().warp();
        this.clock.set(200);
        assertEquals(UPDATED, this.service.rename(initial.id(), "Hub").join().status());
        assertNull(this.registry.get("spawn"));
        this.service.setDescription(initial.id(), "new description").join();
        WorldLocation moved = new WorldLocation("nether", 10, 80, 20, 0, 10);
        this.clock.set(150);
        Warp saved = this.service.relocate(initial.id(), "survival", moved).join().warp();
        assertEquals("Hub", saved.name());
        assertEquals("new description", saved.description());
        assertEquals(moved, saved.location());
        assertEquals("survival", saved.server());
        assertNull(saved.creator());
        assertEquals(100, saved.createdAt());
        assertEquals(150, saved.updatedAt());
        assertEquals(saved, this.registry.get(initial.id()));
        assertEquals(saved, this.published.getLast().warp());
        assertEquals("", this.service.setDescription(initial.id(), "").join().warp().description());
    }

    @Test
    void validatesDirectServiceCallsAndReloadedSettings() {
        assertEquals(INVALID_NAME, this.service.set("bad name", "lobby", this.location, null).join().status());
        assertEquals(INVALID_NAME, this.service.set("a".repeat(33), "lobby", this.location, null).join().status());
        verify(this.store, never()).findByName(anyString());
        Warp first = this.service.set("Spawn", "lobby", this.location, null).join().warp();
        when(this.settings.overwriteExisting()).thenReturn(false);
        assertEquals(DUPLICATE_NAME, this.service.set("spawn", "other", this.location, null).join().status());
        assertEquals(INVALID_NAME, this.service.rename(first.id(), "-bad").join().status());
        assertEquals(DESCRIPTION_TOO_LONG, this.service.setDescription(first.id(), "x".repeat(257)).join().status());
        assertSame(first, this.registry.get(first.id()));
        assertEquals(1, this.published.size());
        when(this.settings.namePattern()).thenReturn("[0-9]+");
        assertEquals(INVALID_NAME, this.service.rename(first.id(), "Hub").join().status());
        assertEquals(UPDATED, this.service.rename(first.id(), "123").join().status());
    }

    @Test
    void missingAndConflictingRecordsDoNotPublish() {
        Warp first = this.service.set("Spawn", "lobby", this.location, null).join().warp();
        this.service.set("Taken", "lobby", this.location, null).join();
        this.published.clear();
        assertEquals(DUPLICATE_NAME, this.service.rename(first.id(), "TAKEN").join().status());
        this.store.delete(first.id()).join();
        assertEquals(NOT_FOUND, this.service.rename(first.id(), "Hub").join().status());
        assertEquals(NOT_FOUND, this.service.setDescription(first.id(), "text").join().status());
        assertEquals(NOT_FOUND, this.service.relocate(first.id(), "other", this.location).join().status());
        assertTrue(this.published.isEmpty());
        assertTrue(this.store.find(first.id()).join().isEmpty());
    }

    @Test
    void deletionBetweenReadAndUpdateCannotRecreateRecord() {
        Warp initial = this.service.set("Spawn", "lobby", this.location, null).join().warp();
        doAnswer(invocation -> {
            this.store.warps.remove(initial.id());
            return invocation.callRealMethod();
        }).when(this.store).update(any());
        this.published.clear();
        assertEquals(NOT_FOUND, this.service.rename(initial.id(), "Hub").join().status());
        assertTrue(this.store.find(initial.id()).join().isEmpty());
        assertTrue(this.published.isEmpty());
    }

    @Test
    void onlyCommittedRecordsReachCacheAndMessages() {
        Warp initial = this.service.set("Spawn", "lobby", this.location, null).join().warp();
        CompletableFuture<WarpStore.SaveResult> pending = new CompletableFuture<>();
        doReturn(pending).when(this.store).update(any());
        this.published.clear();
        CompletableFuture<WarpService.Result> operation = this.service.rename(initial.id(), "Hub");
        assertFalse(operation.isDone());
        assertSame(initial, this.registry.get(initial.id()));
        assertTrue(this.published.isEmpty());
        Warp committed = new Warp(initial.id(), "Hub", "stored", "db", this.location, initial.creator(), initial.createdAt(), 500);
        pending.complete(new WarpStore.SaveResult(WarpStore.Status.SUCCESS, committed));
        assertSame(committed, operation.join().warp());
        assertSame(committed, this.registry.get(initial.id()));
        assertSame(committed, this.published.getFirst().warp());
    }

    @Test
    void databaseFailureRemainsExceptionalAndPublishingFailurePreservesSuccess() {
        Warp initial = this.service.set("Spawn", "lobby", this.location, null).join().warp();
        this.published.clear();
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("database unavailable"))).when(this.store).update(any());
        assertThrows(CompletionException.class, () -> this.service.rename(initial.id(), "Hub").join());
        assertSame(initial, this.registry.get(initial.id()));
        assertTrue(this.published.isEmpty());

        WarpService failingPublisher = new WarpService(this.store, this.registry, "lobby", message -> { throw new IllegalStateException("Redis unavailable"); }, this.logger, () -> this.settings);
        WarpService.Result created = failingPublisher.set("Mine", "lobby", this.location, null).join();
        assertEquals(CREATED, created.status());
        assertEquals(created.warp(), this.registry.get("mine"));
        assertTrue(failingPublisher.delete(created.warp().id()).join());
        assertNull(this.registry.get("mine"));
        verify(this.logger, times(2)).warn(contains("publishing failed"), any(Throwable.class));
    }

    @Test
    void deleteSynchronizesOnlySuccessfulRemoval() {
        Warp initial = this.service.set("Spawn", "lobby", this.location, null).join().warp();
        this.published.clear();
        assertTrue(this.service.delete(initial.id()).join());
        assertFalse(this.service.delete(initial.id()).join());
        assertNull(this.registry.get(initial.id()));
        assertEquals(1, this.published.size());
        assertEquals(WarpMessage.Type.DELETE, this.published.getFirst().type());
        assertEquals(initial.id(), this.published.getFirst().id());
    }
}
