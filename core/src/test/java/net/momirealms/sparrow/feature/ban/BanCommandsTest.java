package net.momirealms.sparrow.feature.ban;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.BanStore;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.feature.ban.command.BanCommand;
import net.momirealms.sparrow.feature.ban.command.BanHistoryCommand;
import net.momirealms.sparrow.feature.ban.command.BanIpCommand;
import net.momirealms.sparrow.feature.ban.command.UnbanCommand;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.IpRange;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BanCommandsTest {
    private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
        @Override
        public boolean hasPermission(CommandSender sender, String permission) {
            return !permission.endsWith(".other");
        }
    };
    private final Player sender = mock(Player.class);
    private final CommandManager feedback = mock(CommandManager.class);
    private final BanFeature feature = mock(BanFeature.class);
    private final BanStore store = mock(BanStore.class);
    private SparrowPlugin plugin;

    @BeforeEach
    void setUp() {
        this.plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
        when(this.sender.getName()).thenReturn("Tester");
        when(this.sender.getUniqueId()).thenReturn(UUID.randomUUID());
        when(this.feature.store()).thenReturn(this.store);
        new BanCommand(this.feedback, this.plugin, this.feature).registerCommand(this.manager, Command.newBuilder("ban", CommandMeta.empty()));
        new BanIpCommand(this.feedback, this.plugin, this.feature).registerCommand(this.manager, Command.newBuilder("ban-ip", CommandMeta.empty()));
        new UnbanCommand(this.feedback, this.plugin, this.feature).registerCommand(this.manager, Command.newBuilder("unban", CommandMeta.empty()));
        new BanHistoryCommand(this.feedback, this.plugin, this.feature).registerCommand(this.manager, Command.newBuilder("ban-history", CommandMeta.empty()));
    }

    @Test
    void banWithIpUsesTheLastLoginAddressAndDuration() {
        UUID uuid = UUID.randomUUID();
        BanTarget.PlayerTarget target = new BanTarget.PlayerTarget(uuid, "Steve");
        when(this.feature.resolvePlayer("Steve")).thenReturn(CompletableFuture.completedFuture(Optional.of(target)));
        when(this.plugin.dataStorage().loadPlayer(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(new PlayerData(uuid, "Steve", 0, 0, null, null, "10.0.0.2", 0))));
        BanRecord record = new BanRecord("AB12CD34", uuid, "Steve", IpRange.parse("10.0.0.2"), "being rude", "Tester", "survival", 0, 1, 0, null);
        when(this.feature.ban(any(), any(), anyString(), anyLong(), anyString(), anyBoolean())).thenReturn(CompletableFuture.completedFuture(new BanFeature.Result(record, false)));
        long before = System.currentTimeMillis();

        this.execute("ban Steve being rude -t 1.5h -I -s");

        ArgumentCaptor<Long> expiresAt = ArgumentCaptor.forClass(Long.class);
        verify(this.feature).ban(eq(target), eq(IpRange.parse("10.0.0.2")), eq("being rude"), expiresAt.capture(), eq("Tester"), eq(true));
        assertTrue(expiresAt.getValue() >= before + 5_400_000 && expiresAt.getValue() <= System.currentTimeMillis() + 5_400_000);
    }

    @Test
    void banIpAcceptsWildcardsOnly() {
        BanRecord record = new BanRecord("AB12CD34", null, null, IpRange.parse("1.2.3.*"), "", "Tester", "survival", 0, 0, 0, null);
        when(this.feature.ban(any(), any(), anyString(), anyLong(), anyString(), anyBoolean())).thenReturn(CompletableFuture.completedFuture(new BanFeature.Result(record, false)));

        this.execute("ban-ip 1.2.3.*");
        this.execute("ban-ip Steve");

        verify(this.feature).ban(null, IpRange.parse("1.2.3.*"), "", 0, "Tester", false);
        verifyNoMoreInteractions(ignoreStubs(this.feature));
    }

    @Test
    void historyWithOnlyFlagsListsEveryTarget() {
        when(this.store.countBans(any())).thenReturn(CompletableFuture.completedFuture(0L));
        long before = System.currentTimeMillis();

        this.execute("ban-history --operator Steve -w 7d -a");

        ArgumentCaptor<BanQuery> query = ArgumentCaptor.forClass(BanQuery.class);
        verify(this.store).countBans(query.capture());
        assertNull(query.getValue().target());
        assertEquals("Steve", query.getValue().operator());
        assertTrue(query.getValue().activeOnly());
        assertTrue(query.getValue().since() >= before - 604_800_000L && query.getValue().since() <= query.getValue().now() - 604_800_000L);
        verify(this.feature, never()).resolveTarget(anyString());
    }

    @Test
    void selfBanUsesSelfFeedbackWithoutOtherPermission() {
        UUID uuid = this.sender.getUniqueId();
        BanTarget.PlayerTarget target = new BanTarget.PlayerTarget(uuid, "Tester");
        when(this.feature.resolvePlayer("Tester")).thenReturn(CompletableFuture.completedFuture(Optional.of(target)));
        BanRecord record = new BanRecord("AB12CD34", uuid, "Tester", null, "test reason", "Tester", "survival", 0, 1, 0, null);
        when(this.feature.ban(any(), any(), anyString(), anyLong(), anyString(), anyBoolean())).thenReturn(CompletableFuture.completedFuture(new BanFeature.Result(record, true)));
        try (MockedStatic<BanTexts> texts = mockStatic(BanTexts.class)) {
            texts.when(() -> BanTexts.reason(record.reason())).thenReturn(Component.text(record.reason()));
            texts.when(() -> BanTexts.expiry(eq(record.expiresAt()), anyLong())).thenReturn(Component.text("1h"));
            texts.when(() -> BanTexts.id(record.id())).thenReturn(Component.text(record.id()));
            this.execute("ban Tester test reason -t 1h");
        }
        verify(this.feedback).handleCommandFeedback(same(this.sender), same(MessageConstants.COMMAND_BAN_SUCCESS_SELF), any(Component[].class));
        verify(this.feedback).handleCommandFeedback(same(this.sender), same(MessageConstants.COMMAND_BAN_REPLACED_SELF), any(Component[].class));
    }

    private void execute(String input) {
        this.manager.commandExecutor().executeCommand(this.sender, input).join();
    }
}
