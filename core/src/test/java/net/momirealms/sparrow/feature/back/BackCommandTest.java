package net.momirealms.sparrow.feature.back;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.feature.FeatureManager;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.teleport.TeleportOptions;
import net.momirealms.sparrow.player.teleport.TeleportResult;
import net.momirealms.sparrow.player.teleport.TeleportService;
import net.momirealms.sparrow.player.teleport.TeleportType;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BackCommandTest {
    private static final WorldLocation POINT = new WorldLocation("world", 10, 64, 20, 30, 5);

    private final SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
    private final CommandManager feedback = mock(CommandManager.class);
    private final TeleportService teleports = mock(TeleportService.class);
    private final BackFeature back = mock(BackFeature.class);
    private final BackSettings settings = new BackSettings();
    private final Player player = mock(Player.class);
    private final BukkitSparrowPlayer sparrow = mock(BukkitSparrowPlayer.class);
    private final UUID id = UUID.randomUUID();
    private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
        @Override
        public boolean hasPermission(CommandSender sender, String permission) {
            return permission.isEmpty() || sender.hasPermission(permission);
        }
    };
    private MockedStatic<SparrowPlugin> pluginInstance;
    private MockedStatic<ServerConfig> serverConfig;

    @BeforeEach
    void setUp() {
        when(this.player.getUniqueId()).thenReturn(this.id);
        when(this.player.getName()).thenReturn("Steve");
        when(this.player.hasPermission("sparrow.command.back")).thenReturn(true);
        FeatureManager features = this.plugin.featureManager();
        doReturn(this.back).when(features).feature(BackFeature.ID, BackFeature.class);
        when(this.back.config()).thenReturn(this.settings);
        when(this.back.point(this.id)).thenReturn(POINT);
        when(this.plugin.playerManager().getPlayer(this.player)).thenReturn(this.sparrow);
        when(this.plugin.playerManager().teleportService()).thenReturn(this.teleports);
        when(this.plugin.compatibilityManager().permissionMinimum(any(), any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(2));
        when(this.teleports.teleport(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(TeleportResult.SUCCESS));
        BackCommand command = new BackCommand(this.feedback, this.plugin);
        command.setCommandConfig(new CommandConfig(true, List.of("/back"), "sparrow.command.back"));
        command.registerCommand(this.manager, Command.<CommandSender>newBuilder("back", CommandMeta.empty()).permission("sparrow.command.back"));
        this.pluginInstance = mockStatic(SparrowPlugin.class);
        this.pluginInstance.when(SparrowPlugin::instance).thenReturn(this.plugin);
        this.serverConfig = mockStatic(ServerConfig.class);
        this.serverConfig.when(ServerConfig::serverId).thenReturn("lobby");
    }

    @AfterEach
    void tearDown() {
        this.serverConfig.close();
        this.pluginInstance.close();
    }

    @Test
    void localReturnWaitsForTheServiceBeforeReportingSuccess() {
        CompletableFuture<TeleportResult> pending = new CompletableFuture<>();
        when(this.teleports.teleport(any(), any(), any(), any())).thenReturn(pending);
        this.execute(this.player, "back");
        verify(this.teleports).teleport(this.player, "lobby", POINT, this.settings.teleportOptions());
        verifyNoInteractions(this.feedback);
        pending.complete(TeleportResult.SUCCESS);
        this.verifyFeedback(this.player, MessageConstants.COMMAND_BACK_SUCCESS_SELF);
        verify(this.plugin.dataStorage(), never()).loadPlayer(any(UUID.class));
        verify(this.plugin.playerManager().teleports(), never()).transfer(any(), any(), any());
    }

    @Test
    void previousServerReturnUsesTheSameOptionsAndConnectingFeedback() {
        when(this.back.point(this.id)).thenReturn(null);
        PlayerData data = new PlayerData(this.id, "Steve", 100, 100, "survival", POINT, null, 100);
        CompletableFuture<Optional<PlayerData>> pending = new CompletableFuture<>();
        when(this.plugin.dataStorage().loadPlayer(this.id)).thenReturn(pending);
        when(this.back.switchedFrom(this.sparrow, data)).thenReturn(true);
        when(this.teleports.teleport(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(TeleportResult.CONNECTING));
        this.execute(this.player, "back");
        verifyNoInteractions(this.teleports);
        pending.complete(Optional.of(data));
        verify(this.teleports).teleport(this.player, "survival", POINT, this.settings.teleportOptions());
        this.verifyFeedback(this.player, MessageConstants.COMMAND_BACK_CONNECTING_SELF);
    }

    @Test
    void missingReturnPointDoesNotStartATeleport() {
        when(this.back.point(this.id)).thenReturn(null);
        when(this.plugin.dataStorage().loadPlayer(this.id)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        this.execute(this.player, "back");
        this.verifyFeedback(this.player, MessageConstants.COMMAND_BACK_NONE_SELF);
        verifyNoInteractions(this.teleports);
    }

    @Test
    void targetArgumentRequiresOtherPermissionAndOnlyOthersSkipChecks() {
        Player other = mock(Player.class);
        when(other.getName()).thenReturn("Alex");
        UUID otherId = UUID.randomUUID();
        when(other.getUniqueId()).thenReturn(otherId);
        when(this.back.point(otherId)).thenReturn(POINT);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer("Alex")).thenReturn(other);
            bukkit.when(() -> Bukkit.getPlayer("Steve")).thenReturn(this.player);
            assertThrows(CompletionException.class, () -> this.execute(this.player, "back Alex"));
            assertThrows(CompletionException.class, () -> this.execute(this.player, "back Steve"));
            verifyNoInteractions(this.teleports);
            when(this.player.hasPermission("sparrow.command.back.other")).thenReturn(true);
            this.execute(this.player, "back Alex");
            verify(this.teleports).teleport(other, "lobby", POINT, new TeleportOptions(TeleportType.BACK, 0, 0, true, true));
            this.verifyFeedback(this.player, MessageConstants.COMMAND_BACK_SUCCESS);
            this.execute(this.player, "back Steve");
            verify(this.teleports).teleport(this.player, "lobby", POINT, this.settings.teleportOptions());
        }
    }

    @ParameterizedTest
    @EnumSource(value = TeleportResult.class, names = {"COOLDOWN", "CANCELLED"})
    void serviceRejectionsDoNotProduceAnExtraCommandMessage(TeleportResult result) {
        when(this.teleports.teleport(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(result));
        this.execute(this.player, "back");
        verifyNoInteractions(this.feedback);
    }

    @Test
    void transferFailuresKeepTheirSpecificFeedback() {
        when(this.teleports.teleport(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(TeleportResult.SERVER_OFFLINE));
        this.execute(this.player, "back");
        this.verifyFeedback(this.player, MessageConstants.COMMAND_BACK_SERVER_OFFLINE);
        when(this.teleports.teleport(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(TeleportResult.INVALID));
        this.execute(this.player, "back");
        this.verifyFeedback(this.player, MessageConstants.COMMAND_BACK_INVALID);
        when(this.teleports.teleport(any(), any(), any(), any())).thenReturn(CompletableFuture.failedFuture(new TimeoutException()));
        this.execute(this.player, "back");
        this.verifyFeedback(this.player, MessageConstants.COMMAND_BACK_TIMEOUT);
    }

    private void execute(CommandSender sender, String input) {
        this.manager.commandExecutor().executeCommand(sender, input).join();
    }

    private void verifyFeedback(CommandSender sender, TranslatableComponent.Builder key) {
        verify(this.feedback).handleCommandFeedback(eq(sender), same(key), any(Component[].class));
    }
}
