package net.momirealms.sparrow.feature.home;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.PlayerRef;
import net.momirealms.sparrow.player.teleport.TeleportResult;
import net.momirealms.sparrow.player.teleport.TeleportType;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.testutil.PluginTestContext;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HomeCommandsTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
    private final CommandManager feedback = mock(CommandManager.class);
    private final HomeStore store = mock(HomeStore.class);
    private final Player player = mock(Player.class);
    private final ConsoleCommandSender console = mock(ConsoleCommandSender.class);
    private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
        @Override
        public boolean hasPermission(CommandSender sender, String permission) {
            return permission.isEmpty() || sender.hasPermission(permission);
        }
    };
    private PluginTestContext context;
    private HomeFeature feature;
    private HomeListCommand list;
    private EditHomeCommand edit;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        this.context = new PluginTestContext(this.plugin, "lobby");
        when(this.plugin.commandManager()).thenReturn(this.feedback);
        when(this.plugin.dataStorage().homeStore()).thenReturn(this.store);
        when(this.plugin.configurationManager().featuresConfig().config().home()).thenReturn(new HomeSettings());
        when(this.plugin.playerManager().getOnlinePlayers()).thenReturn(List.of());
        when(this.plugin.playerManager().resolvePlayer("Offline.User")).thenReturn(CompletableFuture.completedFuture(Optional.of(new PlayerRef(this.other, "Offline.User"))));
        when(this.plugin.playerManager().resolvePlayer("Missing")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(this.store.initialize()).thenReturn(CompletableFuture.completedFuture(null));
        when(this.store.loadByOwner(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(this.store.deleteAll(any())).thenReturn(CompletableFuture.completedFuture(2L));
        when(this.store.findByName(any(), anyString())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(this.store.countByOwner(any())).thenReturn(CompletableFuture.completedFuture(0L));
        when(this.store.create(any())).thenAnswer(invocation -> CompletableFuture.completedFuture(new HomeStore.SaveResult(HomeStore.Status.SUCCESS, invocation.getArgument(0))));
        when(this.plugin.messageBrokerManager().publishOneWay(any(HomeChangedMessage.class), eq(""))).thenReturn(CompletableFuture.completedFuture(1L));
        when(this.player.getUniqueId()).thenReturn(this.owner);
        when(this.player.getName()).thenReturn("Self");
        when(this.player.hasPermission(anyString())).thenAnswer(invocation -> !invocation.<String>getArgument(0).startsWith("sparrow.bypass."));
        when(this.console.hasPermission(anyString())).thenReturn(true);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(this.player.getLocation()).thenReturn(new Location(world, 1, 64, 2));
        when(this.plugin.compatibilityManager().permissionLimit(this.player, "sparrow.max-homes", 3)).thenReturn(3);
        when(this.plugin.compatibilityManager().permissionMinimum(any(), anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(2));
        when(this.plugin.playerManager().teleportService().teleport(any(), anyString(), any(), any())).thenReturn(CompletableFuture.completedFuture(TeleportResult.SUCCESS));
        this.feature = new HomeFeature(this.plugin);
        this.feature.loadConfig();
        this.feature.onEnable();
        this.register(new HomeCommand(this.feature));
        this.register(new SetHomeCommand(this.feature));
        this.register(new DelHomeCommand(this.feature));
        this.register(new DelAllHomeCommand(this.feature));
        this.list = new HomeListCommand(this.feature);
        this.register(this.list);
        this.edit = new EditHomeCommand(this.feature);
        this.register(this.edit);
    }

    @AfterEach
    void tearDown() throws IllegalAccessException {
        try {
            if (this.feature != null) {
                this.feature.onDisable();
            }
        } finally {
            this.context.close();
        }
    }

    @Test
    void selectsDefaultThenUniqueAndOnlyShowsListsWhenExplicitlyRequested() {
        Home mine = this.home(this.owner, "矿场");
        Home home = this.home(this.owner, "HOME");
        this.execute(this.player, "home");
        this.feedback(MessageConstants.COMMAND_HOME_EMPTY);
        when(this.store.loadByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture(List.of(mine, home)));
        this.execute(this.player, "home");
        verify(this.plugin.playerManager().teleportService()).teleport(eq(this.player), eq(home.server()), eq(home.location()), argThat(options -> options.type() == TeleportType.HOME && options.warmupSeconds() == 3));
        when(this.store.loadByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture(List.of(mine)));
        this.execute(this.player, "home");
        when(this.store.loadByOwner(this.owner)).thenReturn(CompletableFuture.completedFuture(List.of(mine, this.home(this.owner, "基地"))));
        this.execute(this.player, "home");
        verify(this.feedback, never()).handleCommandFeedback(eq(this.player), argThat(key -> key.key().equals(CommandPanel.MESSAGE_KEY)), any(Component[].class));
        this.feedback(MessageConstants.COMMAND_HOME_NAME_REQUIRED);
        this.execute(this.player, "home-list");
        verify(this.feedback).handleCommandFeedback(eq(this.player), argThat(key -> key.key().equals(CommandPanel.MESSAGE_KEY)), any(Component[].class));
        this.list.setCommandConfig(new CommandConfig(false, List.of("/home-list"), "sparrow.command.home-list"));
        this.execute(this.player, "home");
        this.feedback(MessageConstants.COMMAND_HOME_NAME_REQUIRED);
        this.list.setCommandConfig(new CommandConfig(true, List.of("/home-list"), "sparrow.command.home-list"));
        when(this.player.hasPermission("sparrow.command.home-list")).thenReturn(false);
        this.execute(this.player, "home");
        verify(this.feedback, times(3)).handleCommandFeedback(eq(this.player), same(MessageConstants.COMMAND_HOME_NAME_REQUIRED), any(Component[].class));
        verify(this.plugin.playerManager().teleportService(), times(2)).teleport(any(), anyString(), any(), any());
        verifyNoInteractions(this.plugin.scheduler().platform());
    }

    @Test
    void setsDefaultAndDeletesBySeparateOwnerWithoutTreatingAllAsBulk() {
        this.execute(this.player, "set-home");
        verify(this.store).create(argThat(home -> home.name().equals("home") && home.owner().equals(this.owner) && home.id().version() == 7));
        when(this.store.findByName(this.owner, "home")).thenReturn(CompletableFuture.completedFuture(Optional.of(this.home(this.owner, "home"))));
        this.execute(this.player, "set-home");
        this.feedback(MessageConstants.COMMAND_SET_HOME_EXISTS);
        verify(this.store).create(any());
        verify(this.store, never()).update(any());
        this.execute(this.player, "del-home all");
        verify(this.store).findByName(this.owner, "all");
        this.execute(this.player, "del-home 矿场 Offline.User");
        verify(this.store).findByName(this.other, "矿场");
        when(this.player.hasPermission("sparrow.command.del-home.other")).thenReturn(false);
        clearInvocations(this.plugin.playerManager(), this.store);
        this.execute(this.player, "del-home 矿场 Offline.User");
        this.feedback(MessageConstants.COMMAND_HOME_NO_PERMISSION);
        verifyNoInteractions(this.store);
        verify(this.plugin.playerManager(), never()).resolvePlayer(anyString());
        this.execute(this.console, "del-home home");
        verify(this.feedback).handleCommandFeedback(eq(this.console), same(MessageConstants.COMMAND_HOME_OWNER_REQUIRED), any(Component[].class));
        verifyNoInteractions(this.plugin.scheduler().platform());
    }

    @Test
    void bulkDeletionRequiresConsoleAndNonemptyFiltersAndNeverDropsAnUnknownOwnerFilter() {
        assertThrows(CompletionException.class, () -> this.execute(this.player, "del-all-home --world world"));
        this.execute(this.console, "del-all-home");
        this.execute(this.console, "del-all-home --player Missing --world world");
        verify(this.store, never()).deleteAll(any());
        this.execute(this.console, "del-all-home --world world --server survival --player Offline.User");
        verify(this.store).deleteAll(new HomeStore.Filter(this.other, "survival", "world"));
        this.execute(this.console, "del-all-home --world world");
        verify(this.store).deleteAll(new HomeStore.Filter(null, null, "world"));
        this.execute(this.console, "del-all-home --world \"My World\" --server old");
        verify(this.store).deleteAll(new HomeStore.Filter(null, "old", "My World"));
        verify(this.feedback).handleCommandFeedback(eq(this.console), same(MessageConstants.COMMAND_UNKNOWN_PLAYER), any(Component[].class));
        verify(this.feedback).handleCommandFeedback(eq(this.console), same(MessageConstants.COMMAND_DEL_ALL_HOME_FILTER_REQUIRED), any(Component[].class));
    }

    @Test
    void otherHomesRequirePermissionAndKeepSelfWarmupWithOneRead() {
        Home home = this.home(this.other, "矿场");
        when(this.store.loadByOwner(this.other)).thenReturn(CompletableFuture.completedFuture(List.of(home)));
        when(this.player.hasPermission("sparrow.command.home.other")).thenReturn(false);
        this.execute(this.player, "home Offline.User.矿场");
        verify(this.store, never()).loadByOwner(this.other);
        when(this.player.hasPermission("sparrow.command.home.other")).thenReturn(true);
        CompletableFuture<List<Home>> loading = new CompletableFuture<>();
        when(this.store.loadByOwner(this.other)).thenReturn(loading);
        CompletableFuture<TeleportResult> teleport = new CompletableFuture<>();
        when(this.plugin.playerManager().teleportService().teleport(any(), anyString(), any(), any())).thenReturn(teleport);
        this.execute(this.player, "home Offline.User.矿场");
        verify(this.plugin.playerManager().teleportService(), never()).teleport(any(), anyString(), any(), any());
        CompletableFuture.runAsync(() -> loading.complete(List.of(home))).join();
        verify(this.plugin.playerManager().teleportService()).teleport(eq(this.player), eq(home.server()), eq(home.location()), argThat(options -> options.type() == TeleportType.HOME && options.warmupSeconds() == 3));
        when(this.store.loadByOwner(this.other)).thenReturn(CompletableFuture.completedFuture(List.of()));
        teleport.complete(TeleportResult.SUCCESS);
        verify(this.store).loadByOwner(this.other);
        this.execute(this.player, "home Missing.home");
        this.feedback(MessageConstants.COMMAND_UNKNOWN_PLAYER);
        when(this.store.loadByOwner(this.other)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
        this.execute(this.player, "home Offline.User.矿场");
        this.feedback(MessageConstants.COMMAND_HOME_STORAGE_FAILED);
        verifyNoInteractions(this.plugin.scheduler().platform());
    }

    @Test
    void paginatesOtherHomesWithConfiguredLinksAndClampsTheLastPage() {
        List<Home> homes = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            homes.add(this.home(this.other, "mine" + i));
        }
        when(this.store.loadByOwner(this.other)).thenReturn(CompletableFuture.completedFuture(homes));
        this.list.setCommandConfig(new CommandConfig(true, List.of("/houses"), "sparrow.command.home-list"));
        this.edit.setCommandConfig(new CommandConfig(true, List.of("/manage-home"), "sparrow.command.edit-home"));
        ((AbstractHomeCommand) this.feedback.feature("home")).setCommandConfig(new CommandConfig(true, List.of("/travel-home"), "sparrow.command.home"));
        this.execute(this.player, "home-list other Offline.User 99");
        List<Component> panel = this.panel(this.player);
        List<ClickEvent> clicks = panel.stream().filter(component -> component.clickEvent() != null).map(Component::clickEvent).toList();
        assertEquals(2, panel.stream().filter(component -> component instanceof TranslatableComponent translated && translated.key().equals("command.home-list.entry")).count());
        assertTrue(clicks.contains(ClickEvent.suggestCommand("/houses other Offline.User 1")));
        assertTrue(clicks.contains(ClickEvent.suggestCommand("/houses other Offline.User 2")));
        assertTrue(clicks.stream().allMatch(click -> click.action() == ClickEvent.Action.SUGGEST_COMMAND));
        assertEquals(2, clicks.stream().filter(click -> click.value().startsWith("/travel-home Offline.User.")).count());
        assertEquals(2, clicks.stream().filter(click -> click.value().startsWith("/manage-home Offline.User.")).count());
        when(this.player.hasPermission("sparrow.command.edit-home.other")).thenReturn(false);
        this.execute(this.player, "home-list other Offline.User 99");
        assertFalse(this.panel(this.player).stream().anyMatch(component -> component.clickEvent() != null && component.clickEvent().value().startsWith("/manage-home")));
        when(this.player.hasPermission("sparrow.command.home-list.other")).thenReturn(false);
        clearInvocations(this.store);
        assertThrows(CompletionException.class, () -> this.execute(this.player, "home-list other Offline.User"));
        verify(this.store, never()).loadByOwner(this.other);
        verifyNoInteractions(this.plugin.scheduler().platform());
    }

    @Test
    void editingRequiresBothOperationAndOwnerPermissionsAndRefreshesWithCommittedData() {
        Home home = this.home(this.other, "矿场");
        when(this.player.hasPermission("sparrow.command.edit-home.other")).thenReturn(false);
        this.execute(this.player, "edit-home Offline.User.矿场 rename 基地");
        verify(this.store, never()).findByName(this.other, "矿场");
        this.feedback(MessageConstants.COMMAND_HOME_NO_PERMISSION);
        when(this.player.hasPermission("sparrow.command.edit-home.other")).thenReturn(true);
        when(this.player.hasPermission("sparrow.command.edit-home.rename")).thenReturn(false);
        assertThrows(CompletionException.class, () -> this.execute(this.player, "edit-home Offline.User.矿场 rename 基地"));
        verify(this.store, never()).findByName(this.other, "矿场");
        when(this.player.hasPermission("sparrow.command.edit-home.rename")).thenReturn(true);
        when(this.store.findByName(this.other, "矿场")).thenReturn(CompletableFuture.completedFuture(Optional.of(home)));
        when(this.store.find(home.id())).thenReturn(CompletableFuture.completedFuture(Optional.of(home)));
        CompletableFuture<HomeStore.SaveResult> saving = new CompletableFuture<>();
        when(this.store.update(any())).thenReturn(saving);
        this.execute(this.player, "edit-home Offline.User.矿场 rename 基地");
        verify(this.feedback, never()).handleCommandFeedback(eq(this.player), same(MessageConstants.COMMAND_EDIT_HOME_RENAMED), any(Component[].class));
        Home committed = new Home(home.id(), home.owner(), "基地", "actual-server", home.location(), home.createdAt(), 1234);
        CompletableFuture.runAsync(() -> saving.complete(new HomeStore.SaveResult(HomeStore.Status.SUCCESS, committed))).join();
        this.feedback(MessageConstants.COMMAND_EDIT_HOME_RENAMED);
        assertTrue(this.panel(this.player).stream().anyMatch(component -> ClickEvent.suggestCommand("/home Offline.User.基地").equals(component.clickEvent())));
        assertTrue(this.panel(this.player).stream().anyMatch(component -> Component.text("actual-server").equals(component)));

        when(this.store.update(any())).thenAnswer(invocation -> CompletableFuture.completedFuture(new HomeStore.SaveResult(HomeStore.Status.SUCCESS, invocation.getArgument(0))));
        this.execute(this.player, "edit-home Offline.User.矿场 relocate");
        verify(this.store).update(argThat(updated -> updated.server().equals("lobby") && updated.location().world().equals("world") && updated.owner().equals(this.other)));
        this.feedback(MessageConstants.COMMAND_EDIT_HOME_RELOCATED);
        assertThrows(CompletionException.class, () -> this.execute(this.console, "edit-home Offline.User.矿场 relocate"));
        when(this.store.update(any())).thenReturn(CompletableFuture.completedFuture(new HomeStore.SaveResult(HomeStore.Status.DUPLICATE_NAME, null)));
        clearInvocations(this.feedback);
        this.execute(this.player, "edit-home Offline.User.矿场 rename occupied");
        this.feedback(MessageConstants.COMMAND_EDIT_HOME_EXISTS);
        verify(this.feedback, never()).handleCommandFeedback(eq(this.player), argThat(key -> key.key().equals(CommandPanel.MESSAGE_KEY)), any(Component[].class));
        when(this.store.findByName(this.other, "矿场")).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
        this.execute(this.player, "edit-home Offline.User.矿场");
        this.feedback(MessageConstants.COMMAND_HOME_STORAGE_FAILED);
        verifyNoInteractions(this.plugin.scheduler().platform());
    }

    @Test
    void deletesImmediatelyAndPanelsSuggestConfiguredCommandsWithPermissions() {
        Home home = this.home(this.other, "矿场");
        when(this.store.findByName(this.other, "矿场")).thenReturn(CompletableFuture.completedFuture(Optional.of(home)));
        CommandConfig config = new CommandConfig(true, List.of("/manage-home"), "custom.edit-home");
        this.edit.setCommandConfig(config);
        when(this.plugin.configurationManager().commandsConfig().configDefinition().command("edit-home")).thenReturn(config);
        this.list.setCommandConfig(new CommandConfig(true, List.of("/houses"), "sparrow.command.home-list"));
        this.execute(this.player, "edit-home Offline.User.矿场");
        List<ClickEvent> clicks = this.panel(this.player).stream().map(Component::clickEvent).filter(click -> click != null).toList();
        assertTrue(clicks.containsAll(List.of(
                ClickEvent.suggestCommand("/home Offline.User.矿场"),
                ClickEvent.suggestCommand("/manage-home Offline.User.矿场 rename "),
                ClickEvent.suggestCommand("/manage-home Offline.User.矿场 relocate"),
                ClickEvent.suggestCommand("/manage-home Offline.User.矿场 delete"),
                ClickEvent.suggestCommand("/houses other Offline.User")
        )));
        assertTrue(clicks.stream().allMatch(click -> click.action() == ClickEvent.Action.SUGGEST_COMMAND));
        verify(this.store, never()).delete(any(), any(UUID.class));

        when(this.store.delete(this.other, home.id())).thenReturn(CompletableFuture.completedFuture(true));
        clearInvocations(this.feedback);
        this.execute(this.player, "edit-home Offline.User.矿场 delete");
        verify(this.store).delete(this.other, home.id());
        this.feedback(MessageConstants.COMMAND_DEL_HOME_SUCCESS);
        verify(this.feedback, never()).handleCommandFeedback(eq(this.player), argThat(key -> key.key().equals(CommandPanel.MESSAGE_KEY)), any(Component[].class));
        when(this.store.findByName(this.other, "矿场")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        this.execute(this.player, "edit-home Offline.User.矿场 delete");
        verify(this.store).delete(this.other, home.id());
        this.feedback(MessageConstants.COMMAND_HOME_UNKNOWN);
        clearInvocations(this.store);
        when(this.player.hasPermission("sparrow.command.edit-home.delete")).thenReturn(false);
        assertThrows(CompletionException.class, () -> this.execute(this.player, "edit-home Offline.User.矿场 delete"));
        when(this.player.hasPermission("sparrow.command.edit-home.delete")).thenReturn(true);
        when(this.player.hasPermission("custom.edit-home.other")).thenReturn(false);
        this.execute(this.player, "edit-home Offline.User.矿场 delete");
        verify(this.store, never()).findByName(any(), anyString());
        verify(this.store, never()).delete(any(), any(UUID.class));

        when(this.store.findByName(this.other, "矿场")).thenReturn(CompletableFuture.completedFuture(Optional.of(home)));
        this.list.setCommandConfig(new CommandConfig(false, List.of("/houses"), "sparrow.command.home-list"));
        when(this.console.hasPermission("custom.edit-home.rename")).thenReturn(false);
        this.execute(this.console, "edit-home Offline.User.矿场");
        List<Component> panel = this.panel(this.console);
        assertFalse(panel.stream().anyMatch(component -> component instanceof TranslatableComponent translated
                && List.of("command.panel.label.home_list", "command.panel.label.rename", "command.panel.label.teleport", "command.panel.label.relocate").contains(translated.key())));
        assertTrue(panel.stream().anyMatch(component -> Component.text("/manage-home Offline.User.矿场 delete").equals(component)));
        verifyNoInteractions(this.plugin.scheduler().platform());
    }

    @Test
    void otherSuggestionsWaitAsynchronouslyOrAppearOnNextSynchronousAttempt() {
        when(this.player.hasPermission("sparrow.command.home.other")).thenReturn(false);
        assertTrue(this.feature.suggest(this.player, "Offline.User.", true, "sparrow.command.home").join().isEmpty());
        verify(this.plugin.playerManager(), never()).resolvePlayer(anyString());
        when(this.player.hasPermission("sparrow.command.home.other")).thenReturn(true);
        CompletableFuture<List<Home>> loading = new CompletableFuture<>();
        when(this.store.loadByOwner(this.other)).thenReturn(loading);
        assertTrue(this.feature.suggest(this.player, "Offline.User.", true, "sparrow.command.home").join().isEmpty());
        when(this.feedback.asynchronousCompletion()).thenReturn(true);
        var waiting = this.feature.suggest(this.player, "Offline.User.矿", true, "sparrow.command.home");
        assertFalse(waiting.isDone());
        CompletableFuture.runAsync(() -> loading.complete(List.of(this.home(this.other, "矿场")))).join();
        assertEquals("Offline.User.矿场", waiting.join().getFirst().suggestion());
        when(this.feedback.asynchronousCompletion()).thenReturn(false);
        assertEquals(1, this.feature.suggest(this.player, "Offline.User.", true, "sparrow.command.home").join().size());
        verify(this.store).loadByOwner(this.other);
        verifyNoInteractions(this.plugin.scheduler().platform());
    }

    private void register(AbstractHomeCommand command) {
        String id = command.getFeatureID();
        CommandConfig config = new CommandConfig(true, List.of("/" + id), "sparrow.command." + id);
        command.setCommandConfig(config);
        when(this.feedback.feature(id)).thenReturn(command);
        when(this.plugin.configurationManager().commandsConfig().configDefinition().command(id)).thenReturn(config);
        command.registerCommand(this.manager, Command.<CommandSender>newBuilder(id, CommandMeta.empty()).permission(config.getPermission()));
    }

    private List<Component> panel(CommandSender sender) {
        ArgumentCaptor<Component[]> arguments = ArgumentCaptor.forClass(Component[].class);
        verify(this.feedback, atLeastOnce()).handleCommandFeedback(eq(sender), argThat(key -> key.key().equals(CommandPanel.MESSAGE_KEY)), arguments.capture());
        ArrayDeque<Component> pending = new ArrayDeque<>(List.of(arguments.getValue()));
        List<Component> components = new ArrayList<>();
        while (!pending.isEmpty()) {
            Component component = pending.removeFirst();
            components.add(component);
            pending.addAll(component.children());
            if (component instanceof TranslatableComponent translated) {
                for (var argument : translated.arguments()) {
                    if (argument.value() instanceof Component value) {
                        pending.add(value);
                    }
                }
            }
        }
        return components;
    }

    private Home home(UUID owner, String name) {
        return new Home(UUID.randomUUID(), owner, name, "survival", new WorldLocation(name, 1, 64, 2, 0, 0), 10, 20);
    }

    private void execute(CommandSender sender, String input) {
        this.manager.commandExecutor().executeCommand(sender, input).join();
    }

    private void feedback(TranslatableComponent key) {
        verify(this.feedback, atLeastOnce()).handleCommandFeedback(eq(this.player), same(key), any(Component[].class));
    }
}
