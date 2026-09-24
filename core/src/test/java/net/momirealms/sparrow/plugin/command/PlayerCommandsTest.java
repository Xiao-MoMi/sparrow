package net.momirealms.sparrow.plugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.util.Index;
import net.momirealms.sparrow.plugin.SparrowPlugin;
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

class PlayerCommandsTest {
    private Server previousServer;
    private CommandSender console;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private TestManager commands;
    private final ArrayDeque<Scheduled> tasks = new ArrayDeque<>();

    @BeforeEach
    void setUp() throws Exception {
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
        SparrowPlugin plugin = (SparrowPlugin) ((Unsafe) field.get(null)).allocateInstance(SparrowPlugin.class);
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

    private Player player(String name) {
        double[] health = {20.0};
        int[] food = {20};
        float[] saturation = {5.0f};
        boolean[] op = {false};
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
