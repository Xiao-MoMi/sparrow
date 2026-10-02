package net.momirealms.sparrow.feature.bed;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.teleport.TeleportOptions;
import net.momirealms.sparrow.player.teleport.TeleportResult;
import net.momirealms.sparrow.player.teleport.TeleportService;
import net.momirealms.sparrow.player.teleport.TeleportType;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BedCommandTest {
    private final SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
    private final CommandManager feedback = mock(CommandManager.class);
    private final TeleportService teleports = mock(TeleportService.class);
    private final BedSettings settings = new BedSettings();
    private final BedFeature feature = new BedFeature(this.plugin);
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private final Location bed = new Location(this.world, 20.5, 70, -30.5, 45, 10);
    private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
        @Override
        public boolean hasPermission(CommandSender sender, String permission) {
            return permission.isEmpty() || sender.hasPermission(permission);
        }
    };
    private MockedStatic<ServerConfig> serverConfig;
    private MockedStatic<SparrowPlugin> pluginInstance;

    @BeforeEach
    void setUp() {
        when(this.player.getName()).thenReturn("Steve");
        when(this.player.getRespawnLocation()).thenReturn(this.bed);
        when(this.player.getPotentialRespawnLocation()).thenReturn(this.bed);
        when(this.world.getName()).thenReturn("world");
        when(this.player.hasPermission("sparrow.command.bed")).thenReturn(true);
        when(this.plugin.configurationManager().featuresConfig().config().bed()).thenReturn(this.settings);
        this.feature.loadConfig();
        when(this.plugin.playerManager().teleportService()).thenReturn(this.teleports);
        when(this.plugin.compatibilityManager().permissionMinimum(any(), any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(2));
        when(this.teleports.teleport(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(TeleportResult.SUCCESS));
        PlatformExecutor platform = this.plugin.scheduler().platform();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(platform).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(platform).run(any(Runnable.class), any(World.class), anyInt(), anyInt());
        BedCommand command = new BedCommand(this.feedback, this.plugin, this.feature);
        command.setCommandConfig(new CommandConfig(true, List.of("/bed"), "sparrow.command.bed"));
        command.registerCommand(this.manager, Command.<CommandSender>newBuilder("bed", CommandMeta.empty()).permission("sparrow.command.bed"));
        this.serverConfig = mockStatic(ServerConfig.class);
        this.serverConfig.when(ServerConfig::serverId).thenReturn("lobby");
        this.pluginInstance = mockStatic(SparrowPlugin.class);
        this.pluginInstance.when(SparrowPlugin::instance).thenReturn(this.plugin);
    }

    @AfterEach
    void tearDown() {
        this.serverConfig.close();
        this.pluginInstance.close();
    }

    @Test
    void sendsTheBedDestinationWithFeatureOptionsAndWaitsForArrivalBeforeFeedback() {
        CompletableFuture<TeleportResult> pending = new CompletableFuture<>();
        when(this.teleports.teleport(any(), any(), any(), any())).thenReturn(pending);
        this.execute("bed");
        verify(this.teleports).teleport(this.player, "lobby", WorldLocation.from(this.bed), this.settings.teleportOptions());
        verifyNoInteractions(this.feedback);
        pending.complete(TeleportResult.SUCCESS);
        verify(this.feedback).handleCommandFeedback(eq(this.player), same(MessageConstants.COMMAND_BED_SUCCESS_SELF), any(Component[].class));
        verify(this.player, never()).teleportAsync(any(Location.class));
    }

    @Test
    void missingBedDoesNotStartWarmupOrCooldown() {
        when(this.player.getRespawnLocation()).thenReturn(null);
        when(this.player.getPotentialRespawnLocation()).thenReturn(null);
        this.execute("bed");
        verify(this.feedback).handleCommandFeedback(eq(this.player), same(MessageConstants.COMMAND_BED_MISSING_SELF), any(Component[].class));
        verifyNoInteractions(this.teleports);
    }

    @Test
    void targetArgumentRequiresOtherPermissionAndOnlyOthersSkipChecks() {
        Player other = mock(Player.class);
        when(other.getName()).thenReturn("Alex");
        when(other.getRespawnLocation()).thenReturn(this.bed);
        when(other.getPotentialRespawnLocation()).thenReturn(this.bed);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer("Alex")).thenReturn(other);
            bukkit.when(() -> Bukkit.getPlayer("Steve")).thenReturn(this.player);
            assertThrows(CompletionException.class, () -> this.execute("bed Alex"));
            assertThrows(CompletionException.class, () -> this.execute("bed Steve"));
            verifyNoInteractions(this.teleports);
            when(this.player.hasPermission("sparrow.command.bed.other")).thenReturn(true);
            this.execute("bed Alex");
            verify(this.teleports).teleport(other, "lobby", WorldLocation.from(this.bed), new TeleportOptions(TeleportType.BED, 0, 0, true, true));
            verify(this.feedback).handleCommandFeedback(eq(this.player), same(MessageConstants.COMMAND_BED_SUCCESS), any(Component[].class));
            this.execute("bed Steve");
            verify(this.teleports).teleport(this.player, "lobby", WorldLocation.from(this.bed), this.settings.teleportOptions());
        }
    }

    @Test
    void failedTeleportKeepsTheFailureFeedback() {
        when(this.teleports.teleport(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(TeleportResult.FAILED));
        this.execute("bed");
        verify(this.feedback).handleCommandFeedback(eq(this.player), same(MessageConstants.COMMAND_TELEPORT_FAILURE_SELF), any(Component[].class));
    }

    @ParameterizedTest
    @EnumSource(value = TeleportResult.class, names = {"COOLDOWN", "CANCELLED"})
    void serviceRejectionsDoNotProduceAnExtraCommandMessage(TeleportResult result) {
        when(this.teleports.teleport(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(result));
        this.execute("bed");
        verifyNoInteractions(this.feedback);
    }

    private void execute(String input) {
        this.manager.commandExecutor().executeCommand(this.player, input).join();
    }
}
