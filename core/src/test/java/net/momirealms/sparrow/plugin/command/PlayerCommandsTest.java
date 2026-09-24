package net.momirealms.sparrow.plugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.util.Index;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerManager;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.bukkit.data.ProtoItemStack;
import net.momirealms.sparrow.plugin.command.feature.ActionBarCommand;
import net.momirealms.sparrow.plugin.command.feature.BroadcastCommand;
import net.momirealms.sparrow.plugin.command.feature.TitleCommand;
import net.momirealms.sparrow.plugin.command.feature.TotemAnimationCommand;
import net.momirealms.sparrow.plugin.command.feature.DemoCommand;
import net.momirealms.sparrow.plugin.command.feature.CreditsCommand;
import net.momirealms.sparrow.compatibility.CompatibilityManager;
import org.incendo.cloud.parser.flag.CommandFlag;
import net.momirealms.sparrow.plugin.command.feature.FlySpeedCommand;
import net.momirealms.sparrow.plugin.command.feature.WalkSpeedCommand;
import net.momirealms.sparrow.plugin.command.feature.SuicideCommand;
import net.momirealms.sparrow.plugin.command.feature.BurnCommand;
import net.momirealms.sparrow.plugin.command.feature.ExtinguishCommand;
import net.momirealms.sparrow.plugin.command.feature.SudoCommand;
import net.momirealms.sparrow.plugin.command.feature.LookCommand;
import org.incendo.cloud.bukkit.data.MultipleEntitySelector;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.context.CommandContext;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Block;
import net.momirealms.sparrow.plugin.command.feature.TopBlockCommand;
import java.lang.reflect.Method;
import java.util.Collection;
import net.momirealms.sparrow.plugin.command.feature.AnvilCommand;
import net.momirealms.sparrow.plugin.command.feature.CartographyTableCommand;
import net.momirealms.sparrow.plugin.command.feature.FeedCommand;
import net.momirealms.sparrow.plugin.command.feature.GrindstoneCommand;
import net.momirealms.sparrow.plugin.command.feature.HealCommand;
import net.momirealms.sparrow.plugin.command.feature.LoomCommand;
import net.momirealms.sparrow.plugin.command.feature.SmithingTableCommand;
import net.momirealms.sparrow.plugin.command.feature.StonecutterCommand;
import net.momirealms.sparrow.plugin.command.feature.WorkbenchCommand;
import net.momirealms.sparrow.plugin.configuration.CommandsConfig;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.scheduler.SchedulerAdapter;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.incendo.cloud.bukkit.BukkitCommandContextKeys;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerCommandsTest {
    private SparrowPlugin plugin;
    private final List<String> performed = new ArrayList<>();
    private Server previousServer;
    private Object previousConfig;
    private CommandSender console;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private TestManager commands;
    private final ArrayDeque<Scheduled> tasks = new ArrayDeque<>();

    @BeforeEach
    void setUp() throws Exception {
        Field configField = PluginConfig.class.getDeclaredField("config");
        configField.setAccessible(true);
        this.previousConfig = configField.get(null);
        configField.set(null, new PluginConfig.ConfigDefinition());
        this.previousServer = Bukkit.getServer();
        this.console = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class}, (instance, method, args) -> method.getName().equals("hasPermission") ? true : null);
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class}, (instance, method, args) -> switch (method.getName()) {
            case "getPlayer" -> this.players.get(args[0]);
            case "getOnlinePlayers" -> this.players.values();
            case "getLogger" -> Logger.getLogger("PlayerCommandsTest");
            default -> null;
        });
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, server);
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        this.plugin = (SparrowPlugin) ((Unsafe) field.get(null)).allocateInstance(SparrowPlugin.class);
        Field compatibilityField = SparrowPlugin.class.getDeclaredField("compatibilityManager");
        compatibilityField.setAccessible(true);
        compatibilityField.set(this.plugin, new CompatibilityManager(this.plugin));
        PlatformExecutor executor = (PlatformExecutor) Proxy.newProxyInstance(PlatformExecutor.class.getClassLoader(), new Class<?>[]{PlatformExecutor.class}, (instance, method, args) -> {
            if (method.getName().equals("run") && args.length == 3) {
                this.tasks.add(new Scheduled((Entity) args[2], (Runnable) args[0]));
                return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        SchedulerAdapter scheduler = (SchedulerAdapter) Proxy.newProxyInstance(SchedulerAdapter.class.getClassLoader(), new Class<?>[]{SchedulerAdapter.class}, (instance, method, args) -> executor);
        Field schedulerField = SparrowPlugin.class.getDeclaredField("scheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(plugin, scheduler);
        this.commands = new TestManager(plugin);
        List<CommandFeature> features = List.of(
                new WorkbenchCommand(this.commands, plugin), new AnvilCommand(this.commands, plugin),
                new GrindstoneCommand(this.commands, plugin), new SmithingTableCommand(this.commands, plugin),
                new StonecutterCommand(this.commands, plugin), new CartographyTableCommand(this.commands, plugin),
                new LoomCommand(this.commands, plugin), new HealCommand(this.commands, plugin), new FeedCommand(this.commands, plugin));
        CommandsConfig.ConfigDefinition config = new CommandsConfig.ConfigDefinition();
        for (int i = 0; i < features.size(); i++) {
            CommandFeature feature = features.get(i);
            this.commands.registerFeature(feature, config.command(feature.getFeatureID()));
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        Field field = Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, this.previousServer);
        Field configField = PluginConfig.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, this.previousConfig);
    }

    @Test
    void allDefaultEntrypointsResolveSelfAndRequireConsoleTarget() throws Exception {
        Player player = this.player("Tester");
        player.setOp(true);
        for (String id : this.commands.features().keys()) {
            for (String prefix : List.of("sparrow ", "")) {
                this.execute(player, prefix + id);
                assertSame(player, this.tasks.removeFirst().entity());
                this.execute(this.console, prefix + id);
                assertEquals("command.player.required", this.commands.feedback.getLast());
                assertTrue(this.tasks.isEmpty());
            }
        }
    }

    @Test
    void targetParsingPermissionsAndCompletion() throws Exception {
        Player sender = this.player("Sender");
        Player target = this.player("Target");
        assertThrows(Exception.class, () -> this.execute(sender, "heal"));
        assertTrue(this.tasks.isEmpty());
        sender.setOp(true);
        for (String id : this.commands.features().keys()) {
            this.execute(sender, "sparrow " + id + " Target");
            assertSame(target, this.tasks.removeFirst().entity());
            this.execute(this.console, id + " Target");
            assertSame(target, this.tasks.removeFirst().entity());
        }
        assertThrows(Exception.class, () -> this.execute(sender, "feed MissingPlayer"));
        assertTrue(this.tasks.isEmpty());
        assertTrue(this.commands.getCommandManager().suggestionFactory().suggest(sender, "workbench Tar")
                .get(5, TimeUnit.SECONDS).list().stream().anyMatch(suggestion -> suggestion.suggestion().equals("Target")));
    }

    @Test
    void operationUsesTargetSchedulerAndFeedbackNeedsNoExtraTask() throws Exception {
        Player sender = this.player("Sender");
        Player target = this.player("Target");
        sender.setOp(true);
        target.setHealth(5.0);
        target.setFoodLevel(2);
        target.setSaturation(0);
        this.execute(sender, "feed Target");
        assertEquals(5.0, target.getHealth());
        Scheduled operation = this.tasks.removeFirst();
        assertSame(target, operation.entity());
        operation.action().run();
        assertEquals(5.0, target.getHealth());
        assertEquals(20, target.getFoodLevel());
        assertEquals(10.0f, target.getSaturation());
        assertTrue(this.tasks.isEmpty());
        assertEquals(List.of("command.feed.success"), this.commands.feedback);
    }

    @Test
    void feedPreservesHealthAndHealDoesNotReviveDeadPlayers() throws Exception {
        Player player = this.player("Tester");
        player.setOp(true);
        player.setHealth(3.0);
        player.setFoodLevel(1);
        this.execute(player, "sparrow feed");
        this.drain();
        assertEquals(3.0, player.getHealth());
        assertEquals(20, player.getFoodLevel());
        assertEquals(10.0f, player.getSaturation());
        player.setHealth(0.0);
        this.execute(player, "sparrow heal");
        this.drain();
        assertEquals(0.0, player.getHealth());
        assertEquals("command.player.dead", this.commands.feedback.getLast());
    }

    @Test
    void speedBoundsAndSuicideUsePlayerScheduler() throws Exception {
        for (CommandFeature feature : List.of(new FlySpeedCommand(this.commands, this.plugin), new WalkSpeedCommand(this.commands, this.plugin), new SuicideCommand(this.commands, this.plugin))) {
            this.commands.registerFeature(feature, new CommandsConfig.ConfigDefinition().command(feature.getFeatureID()));
        }
        Player player = this.player("Tester");
        player.setOp(true);
        this.execute(player, "fly-speed -1");
        assertEquals(0.1f, player.getFlySpeed());
        this.drain();
        assertEquals(-1.0f, player.getFlySpeed());
        this.execute(this.console, "sparrow walk-speed 1 Tester");
        this.drain();
        assertEquals(1.0f, player.getWalkSpeed());
        assertThrows(Exception.class, () -> this.execute(player, "fly-speed 1.01"));
        assertThrows(Exception.class, () -> this.execute(player, "walk-speed -1.01"));
        assertThrows(Exception.class, () -> this.execute(player, "walk-speed NaN"));
        assertTrue(this.tasks.isEmpty());
        this.execute(this.console, "fly-speed 0.5");
        assertEquals("command.player.required", this.commands.feedback.getLast());
        assertThrows(Exception.class, () -> this.execute(this.console, "suicide"));
        this.execute(player, "sparrow suicide");
        assertEquals(20.0, player.getHealth());
        this.drain();
        assertEquals(0.0, player.getHealth());
    }

    @Test
    void burnAndExtinguishScheduleEverySelectedEntityAndRejectEmptySelection() throws Exception {
        Player first = this.player("First");
        Player second = this.player("Second");
        CommandContext<CommandSender> context = new CommandContext<>(this.console, this.commands.getCommandManager());
        MultipleEntitySelector selector = new MultipleEntitySelector() {
            @Override public String inputString() { return "@a"; }
            @Override public Collection<Entity> values() { return List.of(first, second); }
        };
        context.store("targets", selector);
        context.store("time", 1200);
        this.invoke(new BurnCommand(this.commands, this.plugin), context);
        assertEquals(2, this.tasks.size());
        assertEquals(0, first.getFireTicks());
        this.drain();
        assertEquals(1200, first.getFireTicks());
        assertEquals(1200, second.getFireTicks());
        this.invoke(new ExtinguishCommand(this.commands, this.plugin), context);
        this.drain();
        assertEquals(0, first.getFireTicks());
        assertEquals(0, second.getFireTicks());
        context.store("targets", new MultipleEntitySelector() {
            @Override public String inputString() { return "@e"; }
            @Override public Collection<Entity> values() { return List.of(); }
        });
        this.invoke(new BurnCommand(this.commands, this.plugin), context);
        assertTrue(this.tasks.isEmpty());
        assertEquals("command.targets.empty", this.commands.feedback.getLast());
    }

    @Test
    void lookUsesCorrectDiagonalAnglesAndRejectsCrossWorldTargets() throws Exception {
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (instance, method, args) -> method.getName().equals("equals") ? instance == args[0] : null);
        Location location = new Location(world, 0, 64, 0, 0, 15);
        Entity entity = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class}, (instance, method, args) -> switch (method.getName()) {
            case "getLocation" -> location.clone();
            case "getName" -> "Entity";
            case "setRotation" -> { location.setYaw((float) args[0]); location.setPitch((float) args[1]); yield null; }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        LookCommand command = new LookCommand(this.commands, this.plugin);
        CommandContext<CommandSender> context = new CommandContext<>(this.console, this.commands.getCommandManager());
        this.invoke(command, context);
        assertEquals("command.look.options", this.commands.feedback.getLast());
        Method schedule = LookCommand.class.getDeclaredMethod("schedule", CommandContext.class, Collection.class, Location.class, BlockFace.class);
        schedule.setAccessible(true);
        schedule.invoke(command, context, List.of(entity), null, BlockFace.NORTH_EAST);
        this.drain();
        assertEquals(-135.0f, location.getYaw());
        assertEquals(15.0f, location.getPitch());
        schedule.invoke(command, context, List.of(entity), null, BlockFace.NORTH_WEST);
        this.drain();
        assertEquals(135.0f, location.getYaw());
        schedule.invoke(command, context, List.of(entity), new Location(null, 1, 64, 0), null);
        this.drain();
        assertEquals("command.look.different_world", this.commands.feedback.getLast());
    }

    @Test
    void topBlockRejectsDestinationWithoutHeadroomAtWorldLimit() throws Exception {
        Block block = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class}, (instance, method, args) -> switch (method.getName()) {
            case "getY" -> 319;
            case "isPassable" -> false;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (instance, method, args) -> switch (method.getName()) {
            case "getHighestBlockAt" -> block;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (instance, method, args) -> switch (method.getName()) {
            case "getLocation" -> new Location(world, 5.25, 60, -3.75, 90, 20);
            case "getHeight" -> 1.8;
            case "hasPermission" -> true;
            case "getName" -> "Tester";
            default -> throw new UnsupportedOperationException(method.getName());
        });
        TopBlockCommand command = new TopBlockCommand(this.commands, this.plugin);
        this.invoke(command, new CommandContext<>(player, this.commands.getCommandManager()));
        assertSame(player, this.tasks.peekFirst().entity());
        this.drain();
        assertEquals("command.top-block.unavailable", this.commands.feedback.getLast());
    }

    @Test
    void demoAndCreditsResolveBothEntrypointsAndRequireConsoleTarget() throws Exception {
        Player player = this.player("Tester");
        BukkitSparrowPlayer receiver = this.receiver(player);
        player.setOp(true);
        for (CommandFeature feature : List.of(new DemoCommand(this.commands, this.plugin), new CreditsCommand(this.commands, this.plugin))) {
            this.commands.registerFeature(feature, new CommandsConfig.ConfigDefinition().command(feature.getFeatureID()));
            this.execute(player, feature.getFeatureID());
            assertTrue(this.tasks.isEmpty());
            this.execute(this.console, "sparrow " + feature.getFeatureID() + " Tester");
            assertTrue(this.tasks.isEmpty());
            this.execute(this.console, feature.getFeatureID());
            assertEquals("command.player.required", this.commands.feedback.getLast());
        }
        verify(receiver, times(2)).sendDemo();
        verify(receiver, times(2)).sendCredits();
        verifyNoMoreInteractions(receiver);
    }

    @Test
    void messageAndAnimationCommandsRejectEmptySelectionsAndHonorSilent() throws Exception {
        CommandContext<CommandSender> context = new CommandContext<>(this.console, this.commands.getCommandManager());
        context.store("targets", new MultiplePlayerSelector() {
            @Override public String inputString() { return "@a"; }
            @Override public Collection<Player> values() { return List.of(); }
        });
        List<BukkitCommandFeature> commands = List.of(new ActionBarCommand(this.commands, this.plugin),
                new BroadcastCommand(this.commands, this.plugin), new TitleCommand(this.commands, this.plugin),
                new TotemAnimationCommand(this.commands, this.plugin));
        for (int i = 0; i < commands.size(); i++) {
            this.invoke(commands.get(i), context);
            assertEquals("command.targets.empty", this.commands.feedback.getLast());
        }
        assertTrue(this.tasks.isEmpty());
        this.commands.feedback.clear();
        context.flags().addPresenceFlag(CommandFlag.builder("silent").build());
        this.invoke(commands.getFirst(), context);
        assertTrue(this.commands.feedback.isEmpty());
    }

    @Test
    void titleRejectsExtraSeparatorsAndImmediatelySendsEmptySubtitle() throws Exception {
        Player player = this.player("Tester");
        BukkitSparrowPlayer receiver = this.receiver(player);
        CommandContext<CommandSender> context = new CommandContext<>(this.console, this.commands.getCommandManager());
        context.store("targets", new MultiplePlayerSelector() {
            @Override public String inputString() { return "Tester"; }
            @Override public Collection<Player> values() { return List.of(player); }
        });
        context.store("message", "main\\nsub\\nextra");
        TitleCommand command = new TitleCommand(this.commands, this.plugin);
        this.invoke(command, context);
        assertEquals("command.title.format", this.commands.feedback.getLast());
        assertTrue(this.tasks.isEmpty());
        verifyNoInteractions(receiver);
        context.store("message", "main\\n");
        context.store("fadeIn", 5);
        context.store("stay", 60);
        context.store("fadeOut", 15);
        this.invoke(command, context);
        verify(receiver).sendTitle(Component.text("main"), Component.empty(), 5, 60, 15);
        assertTrue(this.tasks.isEmpty());
    }

    @Test
    void messagesParseForReceiverAndSendImmediatelyWithSilentFeedback() throws Exception {
        Player player = this.player("Tester");
        BukkitSparrowPlayer receiver = this.receiver(player);
        CommandContext<CommandSender> context = new CommandContext<>(this.console, this.commands.getCommandManager());
        context.store("targets", new MultiplePlayerSelector() {
            @Override public String inputString() { return "Tester"; }
            @Override public Collection<Player> values() { return List.of(player); }
        });
        context.store("message", "<gold>Hello %player_name%");
        context.flags().addPresenceFlag(CommandFlag.builder("silent").build());
        CompatibilityManager compatibility = mock(CompatibilityManager.class);
        Field field = SparrowPlugin.class.getDeclaredField("compatibilityManager");
        field.setAccessible(true);
        field.set(this.plugin, compatibility);
        when(compatibility.parsePlaceholders(same(player), eq("<gold>Hello %player_name%"))).thenReturn("<gold>Hello Tester");
        this.invoke(new BroadcastCommand(this.commands, this.plugin), context);
        this.invoke(new ActionBarCommand(this.commands, this.plugin), context);
        Component expected = Component.text("Hello Tester", NamedTextColor.GOLD);
        verify(receiver).sendMessage(expected);
        verify(receiver).sendActionBar(expected);
        verify(compatibility, times(2)).parsePlaceholders(same(player), eq("<gold>Hello %player_name%"));
        assertTrue(this.tasks.isEmpty());
        assertTrue(this.commands.feedback.isEmpty());

        Field parseDefault = PluginConfig.TextOptions.class.getDeclaredField("parsePlaceholder");
        parseDefault.setAccessible(true);
        parseDefault.setBoolean(PluginConfig.text(), false);
        this.invoke(new BroadcastCommand(this.commands, this.plugin), context);
        verify(receiver).sendMessage(Component.text("Hello %player_name%", NamedTextColor.GOLD));
        verify(compatibility, times(2)).parsePlaceholders(same(player), eq("<gold>Hello %player_name%"));

        context.flags().addPresenceFlag(CommandFlag.builder("parse").build());
        this.invoke(new BroadcastCommand(this.commands, this.plugin), context);
        verify(receiver, times(2)).sendMessage(expected);
        verify(compatibility, times(3)).parsePlaceholders(same(player), eq("<gold>Hello %player_name%"));
    }

    @Test
    void totemRejectsOtherItemsAndImmediatelySendsAnimation() throws Exception {
        Player player = this.player("Tester");
        BukkitSparrowPlayer receiver = this.receiver(player);
        CommandContext<CommandSender> context = new CommandContext<>(this.console, this.commands.getCommandManager());
        context.store("targets", new MultiplePlayerSelector() {
            @Override public String inputString() { return "Tester"; }
            @Override public Collection<Player> values() { return List.of(player); }
        });
        ProtoItemStack parsed = mock(ProtoItemStack.class);
        ItemStack item = mock(ItemStack.class);
        when(parsed.createItemStack(1)).thenReturn(item);
        when(item.getType()).thenReturn(Material.STONE);
        context.store("item", parsed);
        TotemAnimationCommand command = new TotemAnimationCommand(this.commands, this.plugin);
        this.invoke(command, context);
        assertEquals("command.totem-animation.invalid", this.commands.feedback.getLast());
        verifyNoInteractions(receiver);
        when(item.getType()).thenReturn(Material.TOTEM_OF_UNDYING);
        this.invoke(command, context);
        verify(receiver).sendTotemAnimation(item);
        assertEquals("command.totem-animation.success", this.commands.feedback.getLast());
        assertTrue(this.tasks.isEmpty());
    }

    private BukkitSparrowPlayer receiver(Player player) throws Exception {
        PlayerManager manager = mock(PlayerManager.class);
        BukkitSparrowPlayer receiver = mock(BukkitSparrowPlayer.class);
        when(manager.getPlayer(same(player))).thenReturn(receiver);
        Field field = SparrowPlugin.class.getDeclaredField("playerManager");
        field.setAccessible(true);
        field.set(this.plugin, manager);
        return receiver;
    }

    @Test
    void missingPlaceholderApiPreservesText() {
        CompatibilityManager compatibility = new CompatibilityManager(this.plugin);
        String text = "<gold>Hello %player_name%";
        assertEquals(text, compatibility.parsePlaceholders(this.player("Tester"), text));
    }

    private void invoke(BukkitCommandFeature command, CommandContext<CommandSender> context) throws Exception {
        Method method = command.getClass().getDeclaredMethod("execute", CommandContext.class);
        method.setAccessible(true);
        method.invoke(command, context);
    }

    private Player player(String name) {
        double[] health = {20.0};
        int[] food = {20};
        float[] saturation = {5.0f};
        boolean[] op = {false};
        float[] speed = {0.1f, 0.2f};
        int[] fire = {0};
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (instance, method, args) -> switch (method.getName()) {
            case "getName", "toString" -> name;
            case "hasPermission", "isOp" -> op[0];
            case "setOp" -> { op[0] = (boolean) args[0]; yield null; }
            case "getHealth" -> health[0];
            case "setHealth" -> { health[0] = (double) args[0]; yield null; }
            case "getFoodLevel" -> food[0];
            case "setFoodLevel" -> { food[0] = (int) args[0]; yield null; }
            case "getSaturation" -> saturation[0];
            case "setSaturation" -> { saturation[0] = (float) args[0]; yield null; }
            case "isDead" -> health[0] == 0;
            case "canSee" -> true;
            case "getFlySpeed" -> speed[0];
            case "getWalkSpeed" -> speed[1];
            case "setFlySpeed" -> { speed[0] = (float) args[0]; yield null; }
            case "setWalkSpeed" -> { speed[1] = (float) args[0]; yield null; }
            case "getFireTicks" -> fire[0];
            case "setFireTicks" -> { fire[0] = (int) args[0]; yield null; }
            case "performCommand" -> { this.performed.add(name + ":" + args[0]); yield !args[0].equals("missing"); }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        this.players.put(name, player);
        return player;
    }

    private void execute(CommandSender sender, String command) throws Exception {
        this.commands.getCommandManager().commandExecutor().executeCommand(sender, command).get(5, TimeUnit.SECONDS);
    }

    private void drain() {
        while (!this.tasks.isEmpty()) {
            this.tasks.removeFirst().action().run();
        }
    }

    private record Scheduled(Entity entity, Runnable action) {
    }

    private static final class TestCloud extends org.incendo.cloud.CommandManager<CommandSender> {
        private TestCloud() {
            super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
            this.registerCommandPreProcessor(context -> context.commandContext().store(BukkitCommandContextKeys.BUKKIT_COMMAND_SENDER, context.commandContext().sender()));
        }

        @Override
        public boolean hasPermission(CommandSender sender, String permission) {
            return sender.hasPermission(permission);
        }
    }

    private static final class TestManager extends AbstractCommandManager {
        private final List<String> feedback = new ArrayList<>();

        private TestManager(SparrowPlugin plugin) {
            super(plugin, new TestCloud());
        }

        @Override
        public void handleCommandFeedback(CommandSender sender, TranslatableComponent.Builder key, Component... arguments) {
            this.feedback.add(key.build().key());
        }

        @Override
        protected Locale getLocale(CommandSender sender) {
            return Locale.ENGLISH;
        }

        @Override
        public Index<String, CommandFeature> features() {
            return Index.create(CommandFeature::getFeatureID, List.copyOf(this.registeredFeatures));
        }
    }
}
