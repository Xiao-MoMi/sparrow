package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.feature.back.BackCommand;
import net.momirealms.sparrow.feature.bed.BedCommand;
import net.momirealms.sparrow.feature.bed.BedFeature;
import net.momirealms.sparrow.feature.head.HeadCommand;
import net.momirealms.sparrow.feature.server.ServerCommand;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.AbstractCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.incendo.cloud.Command;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.brigadier.permission.BrigadierPermissionPredicate;
import org.incendo.cloud.bukkit.data.MultipleEntitySelector;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.data.SingleEntitySelector;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.bukkit.parser.selector.MultipleEntitySelectorParser;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.bukkit.parser.selector.SingleEntitySelectorParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.internal.CommandNode;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TargetCommandPermissionTest {
    @ParameterizedTest
    @ValueSource(strings = {"anvil", "back", "bed", "burn", "cartography-table", "color", "enchantment-table", "ender-chest", "extinguish", "feed", "fly", "fly-speed", "grindstone", "hat", "head", "heal", "knockback", "look face", "look entity", "look location", "server", "smithing-table", "stonecutter", "top-block", "walk-speed", "workbench", "world"})
    void targetNodesRequireOtherPermissionInTheClientTree(String path) {
        Fixture fixture = new Fixture();
        CommandNode<CommandSender> root = fixture.node(path);
        CommandNode<CommandSender> target = fixture.targetNode(root);
        BrigadierPermissionPredicate<CommandSender, CommandSender> rootPermission = fixture.clientPermission(root);
        BrigadierPermissionPredicate<CommandSender, CommandSender> targetPermission = fixture.clientPermission(target);
        assertTrue(rootPermission.test(fixture.self));
        assertFalse(targetPermission.test(fixture.self));
        assertTrue(target.component().required());
        fixture.otherAllowed = true;
        assertTrue(targetPermission.test(fixture.self));
        fixture.baseAllowed = false;
        assertFalse(rootPermission.test(fixture.self));
        assertFalse(targetPermission.test(fixture.self));
    }

    @Test
    void omittedTargetUsesBasePermissionAndSelfFeedback() {
        Fixture fixture = new Fixture();
        fixture.execute("feed");
        verify(fixture.self).setFoodLevel(20);
        verify(fixture.feedback).handleCommandFeedback(same(fixture.self), same(MessageConstants.COMMAND_FEED_SUCCESS_SELF), any(Component[].class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fly player enabled", "ender-chest player flags", "color color player flags", "head source amount player force", "head source amount player force flags"})
    void argumentsAfterTheTargetAlsoRequireOtherPermission(String path) {
        Fixture fixture = new Fixture();
        BrigadierPermissionPredicate<CommandSender, CommandSender> permission = fixture.clientPermission(fixture.node(path));
        assertFalse(permission.test(fixture.self));
        fixture.otherAllowed = true;
        assertTrue(permission.test(fixture.self));
        fixture.baseAllowed = false;
        assertFalse(permission.test(fixture.self));
    }

    @Test
    void headSourceAndAmountRemainVisibleWithoutOtherPermission() {
        Fixture fixture = new Fixture();
        assertTrue(fixture.clientPermission(fixture.node("head source")).test(fixture.self));
        assertTrue(fixture.clientPermission(fixture.node("head source amount")).test(fixture.self));
    }

    @Test
    void bareFlyTogglesSelfWithOnlyBasePermission() {
        Fixture fixture = new Fixture();
        when(fixture.self.getAllowFlight()).thenReturn(false, true);
        fixture.execute("fly");
        verify(fixture.self).setAllowFlight(true);
        verify(fixture.self).setFlying(true);
        fixture.execute("fly");
        verify(fixture.self).setFlying(false);
        verify(fixture.self).setAllowFlight(false);
        verify(fixture.feedback).handleCommandFeedback(same(fixture.self), same(MessageConstants.COMMAND_FLY_ENABLED_SELF), any(Component[].class));
        verify(fixture.feedback).handleCommandFeedback(same(fixture.self), same(MessageConstants.COMMAND_FLY_DISABLED_SELF), any(Component[].class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fly Self", "fly Self true", "fly Other false", "ender-chest Self", "ender-chest Other --silent"})
    void extendedFlyAndEnderChestRequireOtherPermission(String input) {
        Fixture fixture = new Fixture();
        assertThrows(CompletionException.class, () -> fixture.execute(input));
        verifyNoInteractions(fixture.platform);
    }

    @Test
    void permittedFlyCanSetOtherPlayersFlight() {
        Fixture fixture = new Fixture();
        fixture.otherAllowed = true;
        fixture.execute("fly Other true");
        verify(fixture.other).setAllowFlight(true);
        verify(fixture.other).setFlying(true);
        verify(fixture.feedback).handleCommandFeedback(same(fixture.self), same(MessageConstants.COMMAND_FLY_ENABLED), any(Component[].class));
        verify(fixture.self, never()).setAllowFlight(anyBoolean());
    }

    @Test
    void bareEnderChestOpensSelfWithOnlyBasePermission() {
        Fixture fixture = new Fixture();
        Inventory chest = mock(Inventory.class);
        when(fixture.self.getEnderChest()).thenReturn(chest);
        when(fixture.self.openInventory(chest)).thenReturn(mock(InventoryView.class));
        fixture.execute("ender-chest");
        verify(fixture.self).openInventory(chest);
        verify(fixture.feedback).handleCommandFeedback(same(fixture.self), same(MessageConstants.COMMAND_ENDER_CHEST_SUCCESS_SELF), any(Component[].class));
    }

    @Test
    void permittedEnderChestOpensOtherAndSupportsSilent() {
        Fixture fixture = new Fixture();
        fixture.otherAllowed = true;
        Inventory chest = mock(Inventory.class);
        when(fixture.other.getEnderChest()).thenReturn(chest);
        when(fixture.other.openInventory(chest)).thenReturn(mock(InventoryView.class));
        fixture.execute("ender-chest Other --silent");
        verify(fixture.other).openInventory(chest);
        verifyNoInteractions(fixture.feedback);
        verify(fixture.self, never()).openInventory(any(Inventory.class));
    }

    @Test
    void explicitSelfStillRequiresOtherPermission() {
        Fixture fixture = new Fixture();
        CompletionException denied = assertThrows(CompletionException.class, () -> fixture.execute("feed Self"));
        assertInstanceOf(NoPermissionException.class, denied.getCause());
        verify(fixture.platform, never()).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
    }

    @Test
    void otherTargetWithoutPermissionHasNoEffect() {
        Fixture fixture = new Fixture();
        assertThrows(CompletionException.class, () -> fixture.execute("heal Other"));
        verify(fixture.platform, never()).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
        verify(fixture.other, never()).setHealth(anyDouble());
    }

    @Test
    void permittedExplicitSelfUsesSelfFeedback() {
        Fixture fixture = new Fixture();
        fixture.otherAllowed = true;
        fixture.execute("feed Self");
        verify(fixture.self).setFoodLevel(20);
        verify(fixture.feedback).handleCommandFeedback(same(fixture.self), same(MessageConstants.COMMAND_FEED_SUCCESS_SELF), any(Component[].class));
    }

    @Test
    void permittedOtherUsesNamedFeedback() {
        Fixture fixture = new Fixture();
        fixture.otherAllowed = true;
        fixture.execute("feed Other");
        verify(fixture.other).setFoodLevel(20);
        verify(fixture.self, never()).setFoodLevel(anyInt());
        verify(fixture.feedback).handleCommandFeedback(same(fixture.self), same(MessageConstants.COMMAND_FEED_SUCCESS), aryEq(new Component[]{Component.text("Other")}));
    }

    @Test
    void otherPermissionDoesNotReplaceBasePermission() {
        Fixture fixture = new Fixture();
        fixture.otherAllowed = true;
        fixture.baseAllowed = false;
        assertThrows(RuntimeException.class, () -> fixture.execute("feed Other"));
        verify(fixture.platform, never()).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
    }

    @Test
    void selfSelectorStillRequiresOtherPermission() {
        Fixture fixture = new Fixture();
        assertThrows(CompletionException.class, () -> fixture.execute("extinguish @s"));
        verify(fixture.self, never()).setFireTicks(anyInt());
    }

    @Test
    void deniedMixedSelectorDoesNotPartiallyApplyToSelf() {
        Fixture fixture = new Fixture();
        assertThrows(CompletionException.class, () -> fixture.execute("extinguish @a"));
        verify(fixture.platform, never()).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
        verify(fixture.self, never()).setFireTicks(anyInt());
        verify(fixture.other, never()).setFireTicks(anyInt());
    }

    @Test
    void permittedMixedSelectorDistinguishesEachFeedback() {
        Fixture fixture = new Fixture();
        fixture.otherAllowed = true;
        fixture.execute("extinguish @a");
        verify(fixture.self).setFireTicks(0);
        verify(fixture.other).setFireTicks(0);
        verify(fixture.feedback).handleCommandFeedback(same(fixture.self), same(MessageConstants.COMMAND_EXTINGUISH_SUCCESS_SELF), any(Component[].class));
        verify(fixture.feedback).handleCommandFeedback(same(fixture.self), same(MessageConstants.COMMAND_EXTINGUISH_SUCCESS), aryEq(new Component[]{Component.text("Other")}));
    }

    @Test
    void precedingSpeedDoesNotBypassTargetPermission() {
        Fixture fixture = new Fixture();
        assertThrows(CompletionException.class, () -> fixture.execute("fly-speed 0.1 Self"));
        verify(fixture.platform, never()).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
    }

    @Test
    void omittedSelectorUsesBasePermission() {
        Fixture fixture = new Fixture();
        fixture.execute("extinguish");
        verify(fixture.self).setFireTicks(0);
    }

    @Test
    void burnWithoutSelectorUsesBasePermissionAndSelfFeedback() {
        Fixture fixture = new Fixture();
        fixture.execute("burn 5s");
        verify(fixture.self).setFireTicks(100);
        verify(fixture.other, never()).setFireTicks(anyInt());
        verify(fixture.feedback).handleCommandFeedback(same(fixture.self), same(MessageConstants.COMMAND_BURN_SUCCESS_SELF), any(Component[].class));
    }

    @Test
    void burnWithSelfSelectorRequiresOtherPermission() {
        Fixture fixture = new Fixture();
        assertThrows(CompletionException.class, () -> fixture.execute("burn 20 @s"));
        verify(fixture.platform, never()).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
    }

    @Test
    void burnWithMixedSelectorRequiresOtherPermission() {
        Fixture fixture = new Fixture();
        assertThrows(CompletionException.class, () -> fixture.execute("burn 20 @a"));
        verify(fixture.platform, never()).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
    }

    @Test
    void permittedBurnSelectorAffectsBothTargets() {
        Fixture fixture = new Fixture();
        fixture.otherAllowed = true;
        fixture.execute("burn 20 @a");
        verify(fixture.self).setFireTicks(20);
        verify(fixture.other).setFireTicks(20);
    }

    @Test
    void burnWithoutSelectorRequiresPlayerSender() {
        Fixture fixture = new Fixture();
        CommandSender console = mock(CommandSender.class);
        fixture.manager.commandExecutor().executeCommand(console, "burn 20").join();
        verify(fixture.platform, never()).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
        verify(fixture.feedback).handleCommandFeedback(same(console), same(MessageConstants.COMMAND_PLAYER_REQUIRED), any(Component[].class));
    }

    @Test
    void managementTargetDoesNotRequireOtherPermission() {
        Fixture fixture = new Fixture();
        fixture.execute("demo Other");
        verify(fixture.plugin.playerManager().getPlayer(fixture.other)).sendDemo();
        verify(fixture.feedback).handleCommandFeedback(same(fixture.self), same(MessageConstants.COMMAND_DEMO_SUCCESS), any(Component[].class));
    }

    @Test
    void managementMoreSchedulesTheTargetWithoutOtherPermission() {
        Fixture fixture = new Fixture();
        doNothing().when(fixture.platform).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
        fixture.execute("more 3 Other");
        verify(fixture.platform).run(any(Runnable.class), any(Runnable.class), same(fixture.other));
    }

    @Test
    void suicideRemainsSelfOnly() {
        Fixture fixture = new Fixture();
        fixture.execute("suicide");
        verify(fixture.self).setHealth(0.0);
        assertThrows(RuntimeException.class, () -> fixture.execute("suicide Other"));
        verify(fixture.other, never()).setHealth(anyDouble());
    }

    private static final class Fixture {
        private final Player self = mock(Player.class);
        private final Player other = mock(Player.class);
        private final SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
        private final CommandManager feedback = mock(CommandManager.class);
        private final PlatformExecutor platform = this.plugin.scheduler().platform();
        private boolean baseAllowed = true;
        private boolean otherAllowed;
        private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(CommandSender sender, String permission) {
                return permission.isEmpty() || (Fixture.this.baseAllowed && (!permission.endsWith(".other") || Fixture.this.otherAllowed));
            }
        };

        private Fixture() {
            when(this.self.getName()).thenReturn("Self");
            when(this.other.getName()).thenReturn("Other");
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(this.platform).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
            ArgumentParser<CommandSender, Player> players = (context, input) -> ArgumentParseResult.success(input.readString().equals("Self") ? this.self : this.other);
            ArgumentParser<CommandSender, MultipleEntitySelector> entities = (context, input) -> {
                String argument = input.readString();
                MultipleEntitySelector selector = mock(MultipleEntitySelector.class);
                when(selector.values()).thenReturn(argument.equals("@s") ? List.of(this.self) : List.of(this.self, this.other));
                return ArgumentParseResult.success(selector);
            };
            ArgumentParser<CommandSender, MultiplePlayerSelector> selectedPlayers = (context, input) -> {
                input.readString();
                MultiplePlayerSelector selector = mock(MultiplePlayerSelector.class);
                when(selector.values()).thenReturn(List.of(this.self, this.other));
                return ArgumentParseResult.success(selector);
            };
            ArgumentParser<CommandSender, SingleEntitySelector> singleEntity = (context, input) -> {
                input.readString();
                SingleEntitySelector selector = mock(SingleEntitySelector.class);
                when(selector.single()).thenReturn(this.other);
                return ArgumentParseResult.success(selector);
            };
            try (MockedStatic<PlayerParser> playerParsers = mockStatic(PlayerParser.class); MockedStatic<MultipleEntitySelectorParser> selectors = mockStatic(MultipleEntitySelectorParser.class);
                 MockedStatic<MultiplePlayerSelectorParser> playerSelectors = mockStatic(MultiplePlayerSelectorParser.class); MockedStatic<SingleEntitySelectorParser> singleSelectors = mockStatic(SingleEntitySelectorParser.class)) {
                playerParsers.when(PlayerParser::playerParser).thenReturn(ParserDescriptor.of(players, Player.class));
                selectors.when(MultipleEntitySelectorParser::multipleEntitySelectorParser).thenReturn(ParserDescriptor.of(entities, MultipleEntitySelector.class));
                playerSelectors.when(MultiplePlayerSelectorParser::multiplePlayerSelectorParser).thenReturn(ParserDescriptor.of(selectedPlayers, MultiplePlayerSelector.class));
                singleSelectors.when(SingleEntitySelectorParser::singleEntitySelectorParser).thenReturn(ParserDescriptor.of(singleEntity, SingleEntitySelector.class));
                this.register(new FeedCommand(this.feedback, this.plugin), "feed");
                this.register(new HealCommand(this.feedback, this.plugin), "heal");
                this.register(new MoreCommand(this.feedback, this.plugin), "more");
                this.register(new DemoCommand(this.feedback, this.plugin), "demo");
                this.register(new FlySpeedCommand(this.feedback, this.plugin), "fly-speed");
                this.register(new ExtinguishCommand(this.feedback, this.plugin), "extinguish");
                this.register(new BurnCommand(this.feedback, this.plugin), "burn");
                this.register(new SuicideCommand(this.feedback, this.plugin), "suicide");
                this.register(new AnvilCommand(this.feedback, this.plugin), "anvil");
                this.register(new BackCommand(this.feedback, this.plugin), "back");
                this.register(new BedCommand(this.feedback, this.plugin, new BedFeature(this.plugin)), "bed");
                this.register(new CartographyTableCommand(this.feedback, this.plugin), "cartography-table");
                this.register(new ColorCommand(this.feedback, this.plugin), "color");
                this.register(new EnchantmentTableCommand(this.feedback, this.plugin), "enchantment-table");
                this.register(new EnderChestCommand(this.feedback, this.plugin), "ender-chest");
                this.register(new FlyCommand(this.feedback, this.plugin), "fly");
                this.register(new GrindstoneCommand(this.feedback, this.plugin), "grindstone");
                this.register(new HatCommand(this.feedback, this.plugin), "hat");
                this.register(new HeadCommand(this.feedback, this.plugin), "head");
                this.register(new KnockbackCommand(this.feedback, this.plugin), "knockback");
                this.register(new LookCommand(this.feedback, this.plugin), "look");
                this.register(new ServerCommand(this.feedback, this.plugin), "server");
                this.register(new SmithingTableCommand(this.feedback, this.plugin), "smithing-table");
                this.register(new StonecutterCommand(this.feedback, this.plugin), "stonecutter");
                this.register(new TopBlockCommand(this.feedback, this.plugin), "top-block");
                this.register(new WalkSpeedCommand(this.feedback, this.plugin), "walk-speed");
                this.register(new WorkbenchCommand(this.feedback, this.plugin), "workbench");
                this.register(new WorldCommand(this.feedback, this.plugin), "world");
            }
        }

        private void register(AbstractCommandFeature command, String name) {
            String permission = "vip." + name;
            command.setCommandConfig(new CommandConfig(true, List.of("/" + name), permission));
            command.registerCommand(this.manager, Command.<CommandSender>newBuilder(name, CommandMeta.empty()).permission(permission));
        }

        private void execute(String input) {
            this.manager.commandExecutor().executeCommand(this.self, input).join();
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

        private CommandNode<CommandSender> targetNode(CommandNode<CommandSender> root) {
            ArrayDeque<CommandNode<CommandSender>> nodes = new ArrayDeque<>(root.children());
            while (!nodes.isEmpty()) {
                CommandNode<CommandSender> node = nodes.removeFirst();
                String name = node.component().name();
                if (name.equals("player") || name.equals("targets")) return node;
                nodes.addAll(node.children());
            }
            throw new AssertionError("Missing target node in " + root.component().name());
        }

        private BrigadierPermissionPredicate<CommandSender, CommandSender> clientPermission(CommandNode<CommandSender> node) {
            return new BrigadierPermissionPredicate<>(SenderMapper.identity(), (sender, permission) -> this.manager.testPermission(sender, permission).allowed(), node);
        }
    }
}
