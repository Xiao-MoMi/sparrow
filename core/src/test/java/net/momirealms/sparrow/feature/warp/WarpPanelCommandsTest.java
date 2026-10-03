package net.momirealms.sparrow.feature.warp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.AbstractCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.util.DateTimeUtils;
import net.momirealms.sparrow.util.WorldLocation;
import net.momirealms.sparrow.testutil.PluginTestContext;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.brigadier.permission.BrigadierPermissionPredicate;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.internal.CommandNode;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarpPanelCommandsTest {
    private static final String EDIT_PERMISSION = "staff.warps";

    private final InMemoryWarpStore store = spy(new InMemoryWarpStore());
    private final WarpSettings settings = new WarpSettings();
    private final SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
    private final CommandManager feedback = mock(CommandManager.class);
    private final Player player = mock(Player.class);
    private final CommandSender console = mock(CommandSender.class);
    private final List<Component> panels = new ArrayList<>();
    private final List<WarpMessage> published = new ArrayList<>();
    private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
        @Override
        public boolean hasPermission(CommandSender sender, String permission) {
            return permission.isEmpty() || sender.hasPermission(permission);
        }
    };
    private MockedStatic<ServerConfig> serverConfig;
    private PluginTestContext context;
    private WarpFeature feature;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        this.context = new PluginTestContext(this.plugin, "lobby");
        when(this.plugin.configurationManager().featuresConfig().config().warp()).thenReturn(this.settings);
        when(this.plugin.dataStorage().warpStore()).thenReturn(this.store);
        when(this.player.hasPermission(anyString())).thenReturn(true);
        when(this.console.hasPermission(anyString())).thenReturn(true);
        World world = mock(World.class);
        when(world.getName()).thenReturn("new_world");
        when(this.player.getLocation()).thenReturn(new Location(world, 10.5, 70, -3.25, 45, 10));
        PlatformExecutor platform = this.plugin.scheduler().platform();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(platform).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
        doAnswer(invocation -> {
            TranslatableComponent key = invocation.getArgument(1);
            if (key.key().equals(CommandPanel.MESSAGE_KEY)) this.panels.add(((Component[]) invocation.getRawArguments()[2])[0]);
            return null;
        }).when(this.feedback).handleCommandFeedback(any(), any(TranslatableComponent.class), any(Component[].class));
        var broker = this.plugin.messageBrokerManager().broker();
        doAnswer(invocation -> {
            this.published.add(invocation.getArgument(0));
            return null;
        }).when(broker).publishOneWay(any(WarpMessage.class), eq(""));
        this.store.put(this.warp("Spawn"), this.warp("矿场"));
        this.serverConfig = mockStatic(ServerConfig.class);
        this.serverConfig.when(ServerConfig::serverId).thenReturn("lobby");
        this.feature = new WarpFeature(this.plugin);
        var features = this.plugin.featureManager();
        doReturn(this.feature).when(features).feature(WarpFeature.ID, WarpFeature.class);
        this.feature.loadConfig();
        this.feature.onLoad();
        this.feature.onEnable();
        this.register(new EditWarpCommand(this.feedback, this.plugin, this.feature), "edit-warp", "/admin warp", EDIT_PERMISSION);
        this.register(new WarpListCommand(this.feedback, this.plugin, this.feature), "warp-list", "/places", "players.places");
        AbstractCommandFeature warp = mock(AbstractCommandFeature.class);
        when(warp.commandConfig()).thenReturn(new CommandConfig(true, List.of("/travel"), "players.travel"));
        when(this.feedback.feature("warp")).thenReturn(warp);
    }

    @AfterEach
    void tearDown() throws IllegalAccessException {
        this.feature.onDisable();
        this.serverConfig.close();
        this.context.close();
    }

    @Test
    void informationIncludesAllFieldsAndConfiguredActionLinks() {
        Warp mine = this.feature.registry().get("矿场");
        this.execute(this.player, "edit-warp 矿场");
        List<Object> fields = this.translated("command.edit-warp.info").getFirst().arguments().stream().map(argument -> argument.value()).toList();
        assertEquals(List.of(Component.text(mine.name()), Component.text(mine.id().toString()), Component.text(mine.key()), Component.text(mine.description()),
                Component.text(mine.server()), Component.text("world"), Component.text(1.5), Component.text(64.0), Component.text(-2.5), Component.text(90.0f), Component.text(15.0f),
                Component.text(mine.creator().toString()), Component.text(DateTimeUtils.fullTime(100)), Component.text(DateTimeUtils.fullTime(200))), fields);
        assertEquals(List.of(ClickEvent.suggestCommand("/admin warp 矿场 rename "), ClickEvent.suggestCommand("/admin warp 矿场 description "),
                ClickEvent.runCommand("/admin warp 矿场 relocate"), ClickEvent.runCommand("/admin warp 矿场 delete")), this.clicks());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rename", "description", "relocate", "delete"})
    void eachOperationRequiresBothBaseAndChildPermissionInTheClientTree(String operation) {
        CommandNode<CommandSender> node = this.node("edit-warp name " + operation);
        BrigadierPermissionPredicate<CommandSender, CommandSender> permission = new BrigadierPermissionPredicate<>(SenderMapper.identity(), (sender, required) -> this.manager.testPermission(sender, required).allowed(), node);
        when(this.player.hasPermission(EDIT_PERMISSION + "." + operation)).thenReturn(false);
        assertFalse(permission.test(this.player));
        String input = "edit-warp Spawn " + operation + switch (operation) {
            case "rename" -> " Hub";
            case "description" -> " New description";
            default -> "";
        };
        assertInstanceOf(NoPermissionException.class, assertThrows(CompletionException.class, () -> this.execute(this.player, input)).getCause());
        this.execute(this.player, "edit-warp Spawn");
        ClickEvent action = switch (operation) {
            case "rename", "description" -> ClickEvent.suggestCommand("/admin warp Spawn " + operation + " ");
            default -> ClickEvent.runCommand("/admin warp Spawn " + operation);
        };
        assertFalse(this.clicks().contains(action));
        assertEquals(1, this.translated("command.panel.disabled").size());
        when(this.player.hasPermission(EDIT_PERMISSION + "." + operation)).thenReturn(true);
        assertTrue(permission.test(this.player));
        when(this.player.hasPermission(EDIT_PERMISSION)).thenReturn(false);
        assertFalse(permission.test(this.player));
        assertThrows(CompletionException.class, () -> this.execute(this.player, input));
        assertEquals(2, this.feature.registry().size());
        assertTrue(this.published.isEmpty());
    }

    @Test
    void renamePreservesMetadataAndBroadcastsTheSavedRecord() {
        Warp original = this.feature.registry().get("spawn");
        this.execute(this.console, "edit-warp SPAWN rename 大厅");
        Warp renamed = this.feature.registry().get("大厅");
        assertNull(this.feature.registry().get("spawn"));
        assertEquals(original.id(), renamed.id());
        assertEquals(original.description(), renamed.description());
        assertEquals(original.server(), renamed.server());
        assertEquals(original.location(), renamed.location());
        assertEquals(original.creator(), renamed.creator());
        assertEquals(original.createdAt(), renamed.createdAt());
        assertTrue(renamed.updatedAt() > original.updatedAt());
        assertEquals(renamed, this.store.warps.get(original.id()));
        assertEquals(renamed, this.published.getLast().warp());
        assertEquals(WarpMessage.Type.SAVE, this.published.getLast().type());
        this.verifyFeedback(this.console, MessageConstants.COMMAND_EDIT_WARP_RENAMED);
        assertEquals(Component.text("大厅"), this.translated("command.edit-warp.info").getFirst().arguments().getFirst().value());
    }

    @Test
    void rejectsTakenAndInvalidNamesWithoutMutatingOrBroadcasting() {
        Warp original = this.feature.registry().get("spawn");
        this.execute(this.player, "edit-warp Spawn rename 矿场");
        this.verifyFeedback(this.player, MessageConstants.COMMAND_SET_WARP_EXISTS);
        this.execute(this.player, "edit-warp Spawn rename two words");
        this.execute(this.player, "edit-warp Spawn rename " + "a".repeat(Warp.MAX_NAME_LENGTH + 1));
        verify(this.feedback, times(2)).handleCommandFeedback(eq(this.player), same(MessageConstants.COMMAND_WARP_INVALID_NAME), any(Component[].class));
        assertSame(original, this.feature.registry().get("spawn"));
        assertEquals(original, this.store.warps.get(original.id()));
        assertTrue(this.published.isEmpty());
    }

    @Test
    void descriptionAcceptsSpacesAndTreatsFormattingAsLiteralText() {
        Warp original = this.feature.registry().get("矿场");
        String description = "这里是 <red>矿场 / 资源区";
        this.execute(this.console, "edit-warp 矿场 description " + description);
        Warp edited = this.feature.registry().get("矿场");
        assertEquals(description, edited.description());
        assertEquals(original.id(), edited.id());
        assertEquals(original.location(), edited.location());
        assertEquals(original.creator(), edited.creator());
        assertEquals(original.createdAt(), edited.createdAt());
        assertEquals(edited, this.published.getLast().warp());
        assertEquals(Component.text(description), this.translated("command.edit-warp.info").getFirst().arguments().get(3).value());
        this.verifyFeedback(this.console, MessageConstants.COMMAND_EDIT_WARP_DESCRIPTION);
        this.execute(this.console, "edit-warp 矿场 description " + "a".repeat(Warp.MAX_DESCRIPTION_LENGTH + 1));
        this.verifyFeedback(this.console, MessageConstants.COMMAND_EDIT_WARP_DESCRIPTION_TOO_LONG);
        assertSame(edited, this.feature.registry().get("矿场"));
        assertEquals(1, this.published.size());
    }

    @Test
    void relocateReadsThePositionOnThePlayerThreadAndPreservesMetadata() {
        Warp original = this.feature.registry().get("spawn");
        this.execute(this.player, "edit-warp Spawn relocate");
        verify(this.plugin.scheduler().platform()).run(any(Runnable.class), any(Runnable.class), same(this.player));
        Warp moved = this.feature.registry().get("spawn");
        assertEquals("lobby", moved.server());
        assertEquals(new WorldLocation("new_world", 10.5, 70, -3.25, 45, 10), moved.location());
        assertEquals(original.id(), moved.id());
        assertEquals(original.description(), moved.description());
        assertEquals(original.creator(), moved.creator());
        assertEquals(original.createdAt(), moved.createdAt());
        assertEquals(moved, this.store.warps.get(original.id()));
        assertEquals(moved, this.published.getLast().warp());
        this.verifyFeedback(this.player, MessageConstants.COMMAND_SET_WARP_MOVED);
        assertThrows(CompletionException.class, () -> this.execute(this.console, "edit-warp Spawn relocate"));
    }

    @Test
    void deletingNeedsASecondClickAndKeepsItsPermissionOnConfirmation() {
        Warp original = this.feature.registry().get("spawn");
        this.execute(this.player, "edit-warp Spawn delete");
        assertSame(original, this.feature.registry().get("spawn"));
        assertTrue(this.published.isEmpty());
        assertEquals(List.of(ClickEvent.runCommand("/admin warp Spawn delete confirm " + original.id()), ClickEvent.runCommand("/admin warp Spawn")), this.clicks());
        when(this.player.hasPermission(EDIT_PERMISSION + ".delete")).thenReturn(false);
        assertThrows(CompletionException.class, () -> this.execute(this.player, "edit-warp Spawn delete confirm " + original.id()));
        when(this.player.hasPermission(EDIT_PERMISSION + ".delete")).thenReturn(true);
        this.execute(this.player, "edit-warp Spawn delete confirm " + original.id());
        assertNull(this.feature.registry().get("spawn"));
        assertFalse(this.store.warps.containsKey(original.id()));
        assertEquals(WarpMessage.Type.DELETE, this.published.getLast().type());
        assertEquals(original.id(), this.published.getLast().id());
        this.verifyFeedback(this.player, MessageConstants.COMMAND_DEL_WARP_SUCCESS);
    }

    @Test
    void anOldConfirmationCannotDeleteAReplacementWithTheSameName() {
        Warp original = this.feature.registry().get("spawn");
        this.execute(this.player, "edit-warp Spawn delete");
        this.feature.service().delete(original.id()).join();
        Warp replacement = this.warp("Spawn");
        this.store.put(replacement);
        this.feature.registry().put(replacement);
        this.published.clear();
        this.execute(this.player, "edit-warp Spawn delete confirm " + original.id());
        assertSame(replacement, this.feature.registry().get("spawn"));
        this.verifyFeedback(this.player, MessageConstants.COMMAND_WARP_UNKNOWN);
        assertTrue(this.published.isEmpty());
    }

    @Test
    void writeFailureLeavesTheCacheUnchangedAndReportsTheFailure() {
        Warp original = this.feature.registry().get("spawn");
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("storage unavailable"))).when(this.store).update(any());
        this.execute(this.player, "edit-warp Spawn rename Hub");
        assertSame(original, this.feature.registry().get("spawn"));
        assertNull(this.feature.registry().get("hub"));
        assertTrue(this.published.isEmpty());
        this.verifyFeedback(this.player, MessageConstants.COMMAND_WARP_STORAGE_FAILED);
        assertTrue(this.panels.isEmpty());
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("storage unavailable"))).when(this.store).delete(any());
        this.execute(this.player, "edit-warp Spawn delete confirm " + original.id());
        assertSame(original, this.feature.registry().get("spawn"));
        assertTrue(this.published.isEmpty());
        verify(this.feedback, times(2)).handleCommandFeedback(eq(this.player), same(MessageConstants.COMMAND_WARP_STORAGE_FAILED), any(Component[].class));
    }

    @Test
    void listUsesConfiguredTravelAndEditLinksAndHidesEditingWithoutPermission() {
        this.execute(this.player, "warp-list");
        assertTrue(this.clicks().contains(ClickEvent.runCommand("/travel 矿场")));
        assertTrue(this.clicks().contains(ClickEvent.runCommand("/admin warp 矿场")));
        var row = this.translated("command.warp-list.entry").getLast();
        assertEquals(Component.text("survival"), row.arguments().get(1).value());
        assertEquals(Component.text("world"), row.arguments().get(2).value());
        assertEquals(Component.text("description 矿场"), row.arguments().get(3).value());
        when(this.player.hasPermission(EDIT_PERMISSION)).thenReturn(false);
        when(this.player.hasPermission("players.travel")).thenReturn(false);
        this.execute(this.player, "warp-list");
        assertTrue(this.translated("command.panel.label.edit").isEmpty());
        assertFalse(this.clicks().contains(ClickEvent.runCommand("/travel Spawn")));
        assertFalse(this.clicks().contains(ClickEvent.runCommand("/travel 矿场")));
    }

    @Test
    void listPaginatesAndClampsTheLastPage() {
        for (int i = 0; i < 11; i++) {
            this.feature.registry().put(this.warp("Warp_" + i));
        }
        this.execute(this.player, "warp-list");
        assertEquals(10, this.translated("command.warp-list.entry").size());
        assertTrue(this.clicks().contains(ClickEvent.runCommand("/places 2")));
        this.execute(this.player, "warp-list 999");
        assertEquals(3, this.translated("command.warp-list.entry").size());
        assertEquals(Component.text(2), this.translated("command.panel.header").getFirst().arguments().get(1).value());
        assertTrue(this.clicks().contains(ClickEvent.runCommand("/places 1")));
        assertTrue(this.clicks().contains(ClickEvent.runCommand("/places 2")));
        assertFalse(this.clicks().contains(ClickEvent.runCommand("/places 3")));
        assertEquals(1, this.translated("command.panel.disabled").size());
    }

    @Test
    void restrictedWarpsAreHiddenFromListsSuggestionsAndEditing() throws Exception {
        Field field = WarpSettings.class.getDeclaredField("permissionRestrict");
        field.setAccessible(true);
        field.set(this.settings, true);
        when(this.player.hasPermission(WarpFeature.PERMISSION_PREFIX + "spawn")).thenReturn(false);
        this.execute(this.player, "warp-list");
        assertEquals(1, this.translated("command.warp-list.entry").size());
        assertEquals(List.of("矿场"), this.manager.suggestionFactory().suggestImmediately(this.player, "edit-warp ").list().stream().map(suggestion -> suggestion.suggestion()).toList());
        this.execute(this.player, "edit-warp Spawn rename Hub");
        this.verifyFeedback(this.player, MessageConstants.COMMAND_WARP_UNKNOWN);
        assertTrue(this.published.isEmpty());
        when(this.player.hasPermission(WarpFeature.PERMISSION_PREFIX + "矿场")).thenReturn(false);
        this.execute(this.player, "warp-list");
        assertEquals(1, this.translated("command.panel.empty").size());
    }

    @Test
    void consoleReceivesTextCommandsAndCannotRelocate() {
        this.execute(this.console, "edit-warp Spawn");
        assertTrue(this.clicks().isEmpty());
        List<String> commands = this.translated("command.panel.console.action").stream()
                .map(action -> ((TextComponent) action.arguments().get(1).value()).content()).toList();
        assertEquals(List.of("/admin warp Spawn rename ", "/admin warp Spawn description ", "/admin warp Spawn delete"), commands);
        assertEquals(1, this.translated("command.panel.player_required").size());
        when(this.feedback.feature("edit-warp")).thenReturn(null);
        this.execute(this.player, "warp-list");
        assertTrue(this.translated("command.panel.label.edit").isEmpty());
    }

    private void register(AbstractCommandFeature command, String name, String usage, String permission) {
        command.setCommandConfig(new CommandConfig(true, List.of(usage), permission));
        when(this.feedback.feature(command.getFeatureID())).thenReturn(command);
        command.registerCommand(this.manager, Command.<CommandSender>newBuilder(name, CommandMeta.empty()).permission(permission));
    }

    private void execute(CommandSender sender, String input) {
        this.manager.commandExecutor().executeCommand(sender, input).join();
    }

    private void verifyFeedback(CommandSender sender, TranslatableComponent message) {
        verify(this.feedback, atLeastOnce()).handleCommandFeedback(eq(sender), same(message), any(Component[].class));
    }

    private CommandNode<CommandSender> node(String path) {
        String[] names = path.split(" ");
        CommandNode<CommandSender> node = this.manager.commandTree().getNamedNode(names[0]);
        for (int i = 1; i < names.length; i++) {
            String name = names[i];
            node = node.children().stream().filter(child -> child.component().name().equals(name)).findFirst().orElseThrow();
        }
        return node;
    }

    private List<TranslatableComponent> translated(String key) {
        return this.components().stream().filter(component -> component instanceof TranslatableComponent translated && translated.key().equals(key))
                .map(component -> (TranslatableComponent) component).toList();
    }

    private List<ClickEvent> clicks() {
        return this.components().stream().map(Component::clickEvent).filter(click -> click != null).toList();
    }

    private List<Component> components() {
        List<Component> result = new ArrayList<>();
        ArrayDeque<Component> remaining = new ArrayDeque<>();
        remaining.add(this.panels.getLast());
        while (!remaining.isEmpty()) {
            Component component = remaining.removeFirst();
            result.add(component);
            remaining.addAll(component.children());
            if (component instanceof TranslatableComponent translated) {
                for (var argument : translated.arguments()) {
                    if (argument.value() instanceof Component value) remaining.add(value);
                }
            }
        }
        return result;
    }

    private Warp warp(String name) {
        return new Warp(UUID.randomUUID(), name, "description " + name, "survival", new WorldLocation("world", 1.5, 64, -2.5, 90, 15), UUID.randomUUID(), 100, 200);
    }
}
