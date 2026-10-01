package net.momirealms.sparrow.feature.head;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
    void amountAndForceArgumentsAndEntireHandlerStartsOffTheCommandThread() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Thread caller = Thread.currentThread();
            CompletableFuture<Thread> queryThread = new CompletableFuture<>();
            when(fixture.head.fetchByName("Tester", true)).thenAnswer(invocation -> {
                queryThread.complete(Thread.currentThread());
                return fixture.result;
            });
            fixture.execute("head Tester 65 Tester true");
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
            // flag 只能写在所有位置参数之后
            fixture.execute("head Tester 1 Tester false --silent");
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
            fixture.execute("head " + id + " 1 Tester true");
            fixture.execute("head " + id.toString().replace("-", ""));
            verify(fixture.head, timeout(2000)).fetchByUuid(id, true);
            verify(fixture.head, timeout(2000)).fetchByUuid(id, false);
        }
    }

    @Test
    void headSourceDoesNotRequireOtherPermission() {
        try (Fixture fixture = new Fixture()) {
            fixture.otherAllowed = false;
            fixture.execute("head Other");
            verify(fixture.head, timeout(2000)).fetchByName("Other", false);
        }
    }

    @Test
    void explicitRecipientRequiresOtherBeforeFetchingEvenWithSilent() {
        try (Fixture fixture = new Fixture()) {
            fixture.otherAllowed = false;
            assertThrows(CompletionException.class, () -> fixture.execute("head Tester 1 Tester false --silent"));
            verify(fixture.head, never()).fetchByName(anyString(), anyBoolean());
            assertTrue(fixture.deliveries.isEmpty());
        }
    }

    @Test
    void amountWithoutRecipientUsesBasePermissionAndGivesToSelf() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.otherAllowed = false;
            fixture.execute("head Other 65");
            verify(fixture.head, timeout(2000)).fetchByName("Other", false);
            fixture.result.complete(new HeadData(UUID.randomUUID(), "Other", "texture", null));
            Runnable delivery = fixture.deliveries.poll(2, TimeUnit.SECONDS);
            assertNotNull(delivery);
            delivery.run();
            verify(fixture.head).give(same(fixture.player), any(), eq(65), eq(1L));
            verify(fixture.feedback).handleCommandFeedback(same(fixture.player), same(MessageConstants.COMMAND_HEAD_SUCCESS_SELF), any(Component[].class));
        }
    }

    @Test
    void bareCommandUsesSenderAsSourceAndRecipient() {
        try (Fixture fixture = new Fixture()) {
            fixture.otherAllowed = false;
            fixture.execute("head");
            verify(fixture.head, timeout(2000)).fetchByName("Tester", false);
        }
    }

    @Test
    void forceCannotBeUsedWithoutOtherPermission() {
        try (Fixture fixture = new Fixture()) {
            fixture.otherAllowed = false;
            assertThrows(CompletionException.class, () -> fixture.execute("head Tester 1 Tester true"));
            assertThrows(CompletionException.class, () -> fixture.execute("head Tester 1 true"));
            verify(fixture.head, never()).fetchByName(anyString(), anyBoolean());
        }
    }

    private static final class Fixture implements AutoCloseable {
        private boolean otherAllowed = true;
        private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        private final Player player = mock(Player.class);
        private final CommandManager feedback = mock(CommandManager.class);
        private final HeadFeature head = mock(HeadFeature.class);
        private final CompletableFuture<HeadData> result = new CompletableFuture<>();
        private final LinkedBlockingQueue<Runnable> deliveries = new LinkedBlockingQueue<>();
        private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(CommandSender sender, String permission) { return !permission.endsWith(".other") || Fixture.this.otherAllowed; }
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
            // 测试中没有原版实体选择器, 改用按名称匹配的解析器
            ArgumentParser<CommandSender, MultiplePlayerSelector> selectorParser = (context, input) -> {
                String name = input.readString();
                MultiplePlayerSelector selector = mock(MultiplePlayerSelector.class);
                when(selector.values()).thenReturn(name.equals("Tester") ? List.of(this.player) : List.of());
                return ArgumentParseResult.success(selector);
            };
            try (MockedStatic<MultiplePlayerSelectorParser> selectors = mockStatic(MultiplePlayerSelectorParser.class)) {
                selectors.when(MultiplePlayerSelectorParser::multiplePlayerSelectorParser).thenReturn(ParserDescriptor.of(selectorParser, MultiplePlayerSelector.class));
                HeadCommand command = new HeadCommand(this.feedback, plugin);
                command.setCommandConfig(new CommandConfig(true, List.of("/head"), "sparrow.command.head"));
                command.registerCommand(this.manager, Command.<CommandSender>newBuilder("head", CommandMeta.empty()).permission("sparrow.command.head"));
            }
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
