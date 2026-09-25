package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.head.HeadData;
import net.momirealms.sparrow.feature.head.HeadFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeadCommandTest {
    @BeforeAll
    static void initializeMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void forceWithoutSourceUsesSelfAndEntireHandlerStartsOffTheCommandThread() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Thread caller = Thread.currentThread();
            CompletableFuture<Thread> queryThread = new CompletableFuture<>();
            when(fixture.head.fetchByName("Tester", true)).thenAnswer(invocation -> {
                queryThread.complete(Thread.currentThread());
                return fixture.result;
            });
            fixture.execute("head --force --amount 65");
            assertNotSame(caller, queryThread.get(2, TimeUnit.SECONDS));
            fixture.result.complete(new HeadData(UUID.randomUUID(), "Tester", "texture", null));
            Runnable delivery = fixture.deliveries.poll(2, TimeUnit.SECONDS);
            assertNotNull(delivery);
            verify(fixture.head, never()).give(any(), any(), anyInt(), anyLong());
            delivery.run();
            verify(fixture.head).give(same(fixture.player), any(), eq(65), eq(1L));
        }
    }

    @Test
    void timeoutIsReportedEvenWithSilentAndNeverSchedulesDelivery() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.execute("head Tester --silent");
            verify(fixture.head, timeout(2000)).fetchByName("Tester", false);
            fixture.result.completeExceptionally(new TimeoutException());
            verify(fixture.feedback, timeout(2000)).handleCommandFeedback(same(fixture.player), same(MessageConstants.COMMAND_HEAD_TIMEOUT), any(Component[].class));
            assertTrue(fixture.deliveries.isEmpty());
        }
    }

    @Test
    void moduleGenerationChangeDiscardsSuccessfulOldResults() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.execute("head Tester");
            verify(fixture.head, timeout(2000)).fetchByName("Tester", false);
            when(fixture.head.generation()).thenReturn(2L);
            fixture.result.complete(new HeadData(UUID.randomUUID(), "Tester", "texture", null));
            verify(fixture.feedback, timeout(2000)).handleCommandFeedback(same(fixture.player), same(MessageConstants.COMMAND_HEAD_CANCELLED), any(Component[].class));
            assertTrue(fixture.deliveries.isEmpty());
        }
    }

    @Test
    void dashedAndCompactUuidsUseUuidLookup() throws Exception {
        try (Fixture fixture = new Fixture()) {
            UUID id = UUID.randomUUID();
            fixture.execute("head " + id + " --force");
            fixture.execute("head " + id.toString().replace("-", ""));
            verify(fixture.head, timeout(2000)).fetchByUuid(id, true);
            verify(fixture.head, timeout(2000)).fetchByUuid(id, false);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final Player player = mock(Player.class);
        private final CommandManager feedback = mock(CommandManager.class);
        private final HeadFeature head = mock(HeadFeature.class);
        private final CompletableFuture<HeadData> result = new CompletableFuture<>();
        private final LinkedBlockingQueue<Runnable> deliveries = new LinkedBlockingQueue<>();
        private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(CommandSender sender, String permission) { return true; }
        };

        private Fixture() {
            SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
            var features = plugin.featureManager();
            var scheduler = plugin.scheduler();
            var platform = scheduler.platform();
            when(this.player.getName()).thenReturn("Tester");
            when(plugin.playerManager().getPlayer(this.player)).thenReturn(mock(BukkitSparrowPlayer.class));
            doReturn(this.head).when(features).feature(HeadFeature.ID, HeadFeature.class);
            when(this.head.enabled()).thenReturn(true);
            when(this.head.generation()).thenReturn(1L);
            when(this.head.give(any(), any(), anyInt(), anyLong())).thenReturn(true);
            when(this.head.fetchByName(anyString(), anyBoolean())).thenReturn(this.result);
            when(this.head.fetchByUuid(any(), anyBoolean())).thenReturn(this.result);
            when(scheduler.async()).thenReturn(this.executor);
            doAnswer(invocation -> {
                this.executor.execute(invocation.getArgument(0));
                return null;
            }).when(scheduler).executeAsync(any());
            doAnswer(invocation -> {
                this.deliveries.add(invocation.getArgument(0));
                return null;
            }).when(platform).run(any(Runnable.class), any(Runnable.class), same(this.player));
            new HeadCommand(this.feedback, plugin).registerCommand(this.manager, Command.newBuilder("head", CommandMeta.empty()));
        }

        private void execute(String input) {
            this.manager.commandExecutor().executeCommand(this.player, input).join();
        }

        @Override
        public void close() {
            this.executor.shutdownNow();
        }
    }
}
