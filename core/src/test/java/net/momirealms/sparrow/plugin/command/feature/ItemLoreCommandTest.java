package net.momirealms.sparrow.plugin.command.feature;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.RootCommandNode;
import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.scheduler.SchedulerAdapter;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.Command;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.brigadier.CloudBrigadierManager;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ItemLoreCommandTest {
    private MockedStatic<CraftRegistry> registry;

    @BeforeAll
    static void initializeMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void initializeRegistry() {
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        this.registry = mockStatic(CraftRegistry.class);
        this.registry.when(CraftRegistry::getMinecraftRegistry).thenReturn(registries);
    }

    @AfterEach
    void closeRegistry() {
        this.registry.close();
    }

    @Test
    void queryBuildsEditablePanelUsingConfiguredCommandWithoutWriting() {
        Fixture fixture = new Fixture("lore_alias");
        ItemStack item = fixture.hold("first", "second");
        item.set(DataComponents.LORE, new ItemLore(List.of(
                net.minecraft.network.chat.Component.literal("first").withStyle(style -> style.withColor(0x55ffff).withItalic(false))
                        .append(net.minecraft.network.chat.Component.literal(" nested").withStyle(style -> style.withColor(0xffaa00).withBold(true))),
                net.minecraft.network.chat.Component.literal("second"))));
        fixture.execute("");
        ArgumentCaptor<Component[]> args = ArgumentCaptor.forClass(Component[].class);
        verify(fixture.feedback).handleCommandFeedback(eq(fixture.player), same(MessageConstants.COMMAND_ITEM_LORE_QUERY), args.capture());
        Component panel = args.getValue()[0];
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        assertEquals("[1] first nested [X] [↓]\n[2] second [X] [↑]\n[3] [+]", plain.serialize(panel));
        Component first = panel.children().get(1);
        assertEquals(NamedTextColor.AQUA, first.color());
        assertEquals(TextDecoration.State.FALSE, first.decoration(TextDecoration.ITALIC));
        assertEquals(NamedTextColor.GOLD, first.children().getFirst().color());
        assertEquals(TextDecoration.State.TRUE, first.children().getFirst().decoration(TextDecoration.BOLD));
        String[] sourceLines = plain.serialize((Component) first.hoverEvent().value()).split("\n", -1);
        assertEquals(2, sourceLines.length);
        assertTrue(sourceLines[0].startsWith("JSON: "));
        assertTrue(sourceLines[0].contains("\"text\":\"first\""));
        assertTrue(sourceLines[1].startsWith("MiniMessage: "));
        assertTrue(sourceLines[1].contains("<aqua>"));
        Component second = panel.children().stream().filter(component -> plain.serialize(component).equals("second")).findFirst().orElseThrow();
        assertEquals(NamedTextColor.DARK_PURPLE, second.color());
        assertEquals(TextDecoration.State.TRUE, second.decoration(TextDecoration.ITALIC));
        List<ClickEvent> clicks = fixture.clicks(MessageConstants.COMMAND_ITEM_LORE_QUERY);
        assertTrue(clicks.contains(ClickEvent.runCommand("/lore_alias --operation remove --line 1 --internal 1")));
        assertTrue(clicks.contains(ClickEvent.runCommand("/lore_alias --operation down --line 1 --internal 1")));
        assertTrue(clicks.contains(ClickEvent.runCommand("/lore_alias --operation up --line 2 --internal 1")));
        assertEquals(4, clicks.stream().filter(click -> click.action() == ClickEvent.Action.RUN_COMMAND).count());
        assertFalse(clicks.stream().anyMatch(click -> click.action() == ClickEvent.Action.CUSTOM));
        assertEquals(panel, AdventureHelper.jsonToComponent(AdventureHelper.componentToJson(panel)));
        assertTrue(clicks.contains(ClickEvent.suggestCommand("/lore_alias --operation insert --line 3 --lore ")));
        assertEquals(4, clicks.stream().filter(click -> click.action() == ClickEvent.Action.SUGGEST_COMMAND && click.value().contains("--operation edit")).count());
        verify(fixture.inventory, never()).setSelectedItem(any());
    }

    @Test
    void insertsAndEditsOneLineDirectlyOnTheHeldItem() {
        Fixture fixture = new Fixture();
        ItemStack original = fixture.hold("first", "last");
        original.setCount(7);
        original.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("keep"));
        fixture.execute("--operation insert --line 2 --lore middle with spaces");
        assertEquals(List.of("first", "middle with spaces", "last"), fixture.lines());
        assertEquals(7, fixture.held.getCount());
        assertEquals(original.get(DataComponents.CUSTOM_NAME), fixture.held.get(DataComponents.CUSTOM_NAME));
        assertSame(original, fixture.held);
        assertEquals(3, original.get(DataComponents.LORE).lines().size());
        fixture.execute("--operation edit --line 2 --lore edited");
        assertEquals(List.of("first", "edited", "last"), fixture.lines());
        assertEquals(0xffffff, fixture.held.get(DataComponents.LORE).lines().get(1).getStyle().getColor().getValue());
        assertFalse(fixture.held.get(DataComponents.LORE).lines().get(1).getStyle().isItalic());
    }

    @ParameterizedTest
    @CsvSource({"up,second:first:third", "down,first:third:second", "remove,first:third"})
    void movesOrRemovesTheSpecifiedLine(String operation, String expected) {
        Fixture fixture = new Fixture();
        fixture.hold("first", "second", "third");
        fixture.execute("--operation " + operation + " --line 2");
        assertEquals(Arrays.asList(expected.split(":")), fixture.lines());
    }

    @Test
    void removingTheLastLineRemovesTheComponentAndOffersInsertion() {
        Fixture fixture = new Fixture();
        fixture.hold("only");
        fixture.execute("--operation remove --line 1");
        assertNull(fixture.held.get(DataComponents.LORE));
        assertEquals(List.of(ClickEvent.suggestCommand("/item_lore --operation insert --line 1 --lore ")), fixture.clicks(MessageConstants.COMMAND_ITEM_LORE_SUCCESS));
    }

    @Test
    void insertsIntoAnItemWithoutLore() {
        Fixture fixture = new Fixture();
        fixture.hold();
        fixture.execute("--operation insert --line 1 --lore first");
        assertEquals(List.of("first"), fixture.lines());
    }

    @ParameterizedTest
    @CsvSource({"insert,4", "edit,3", "remove,3", "up,1", "down,2"})
    void rejectsOutOfRangeLinesWithoutWriting(String operation, int line) {
        Fixture fixture = new Fixture();
        fixture.hold("first", "second");
        fixture.execute("--operation " + operation + " --line " + line + " --lore ignored");
        fixture.assertFeedback(MessageConstants.COMMAND_ITEM_LORE_BOUND);
        assertEquals(List.of("first", "second"), fixture.lines());
        verify(fixture.inventory, never()).setSelectedItem(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"--operation insert", "--operation insert --line 1", "--operation edit --line 1"})
    void reportsMissingRequiredFlags(String flags) {
        Fixture fixture = new Fixture();
        fixture.hold("first");
        fixture.execute(flags);
        fixture.assertFeedback(MessageConstants.COMMAND_ITEM_LORE_MISSING_FLAG);
        verify(fixture.inventory, never()).setSelectedItem(any());
    }

    @Test
    void supportsMiniMessageLegacyAndJsonInput() {
        Fixture fixture = new Fixture();
        fixture.hold();
        fixture.execute("--operation insert --line 1 --lore <gold>mini");
        fixture.execute("--operation insert --line 2 --lore &6legacy --legacy-color");
        fixture.execute("--operation insert --line 3 --json --lore {\"text\":\"json\",\"color\":\"gold\",\"italic\":true}");
        assertEquals(List.of("mini", "legacy", "json"), fixture.lines());
        List<net.minecraft.network.chat.Component> lines = fixture.held.get(DataComponents.LORE).lines();
        for (int i = 0; i < lines.size(); i++) {
            assertEquals(0xffaa00, lines.get(i).getStyle().getColor().getValue());
        }
        assertTrue(lines.get(2).getStyle().isItalic());
    }

    @Test
    void rejectsMalformedJsonAndEmptyHandWithoutWriting() {
        Fixture fixture = new Fixture();
        fixture.hold("first");
        fixture.execute("--operation edit --line 1 --json --lore {broken");
        fixture.assertFeedback(MessageConstants.COMMAND_ITEM_LORE_INVALID);
        assertEquals(List.of("first"), fixture.lines());
        fixture.held = ItemStack.EMPTY;
        fixture.execute("");
        fixture.assertFeedback(MessageConstants.COMMAND_ITEM_LORE_ITEMLESS);
        verify(fixture.inventory, never()).setSelectedItem(any());
    }

    @Test
    void nativeLineLimitRejectsInsertionButAllowsEditing() {
        Fixture fixture = new Fixture();
        ItemStack item = fixture.hold();
        item.set(DataComponents.LORE, new ItemLore(Collections.nCopies(ItemLore.MAX_LINES, net.minecraft.network.chat.Component.literal("line"))));
        fixture.execute("--operation insert --line 257 --lore overflow");
        fixture.assertFeedback(MessageConstants.COMMAND_ITEM_LORE_LIMIT);
        verify(fixture.inventory, never()).setSelectedItem(any());
        fixture.execute("--operation edit --line 256 --lore edited");
        assertEquals("edited", fixture.lines().getLast());
        assertEquals(256, fixture.lines().size());
    }

    @Test
    void outdatedPanelsAreRejectedButLatestPanelStillWorks() {
        Fixture fixture = new Fixture();
        fixture.hold("first", "second");
        fixture.execute("");
        fixture.execute("");
        fixture.execute("--operation remove --line 1 --internal 1");
        fixture.assertFeedback(MessageConstants.COMMAND_ITEM_LORE_EXPIRED);
        assertEquals(List.of("first", "second"), fixture.lines());
        verify(fixture.inventory, never()).setSelectedItem(any());
        fixture.execute("--operation remove --line 1 --internal 2");
        assertEquals(List.of("second"), fixture.lines());
        assertEquals(3, fixture.metadata.asInt());
    }

    @Test
    void panelStillUsesTheCurrentMainHandAsInTheOldCommand() {
        Fixture fixture = new Fixture();
        fixture.hold("old");
        fixture.execute("");
        fixture.hold("new first", "new second");
        fixture.execute("--operation remove --line 1 --internal 1");
        assertEquals(List.of("new second"), fixture.lines());
    }

    @ParameterizedTest
    @CsvSource({"up,second:first:third", "down,first:third:second", "remove,first:third"})
    void commandClicksExecuteTheExistingOperationAndRejectReplay(String operation, String expected) {
        Fixture fixture = new Fixture("lore_alias");
        fixture.hold("first", "second", "third");
        fixture.execute("");
        String payload = fixture.clicks(MessageConstants.COMMAND_ITEM_LORE_QUERY).stream()
                .filter(click -> click.action() == ClickEvent.Action.RUN_COMMAND && click.value().contains("--operation " + operation + " --line 2 "))
                .findFirst().orElseThrow().value();
        fixture.click(payload);
        assertEquals(Arrays.asList(expected.split(":")), fixture.lines());
        fixture.click(payload);
        fixture.assertFeedback(MessageConstants.COMMAND_ITEM_LORE_EXPIRED);
        assertEquals(Arrays.asList(expected.split(":")), fixture.lines());
    }

    @Test
    void commandClicksStillRequireCommandPermission() {
        Fixture fixture = new Fixture();
        fixture.hold("first", "second");
        fixture.execute("");
        fixture.allowed = false;
        clearInvocations(fixture.platform);
        CompletionException exception = assertThrows(CompletionException.class,
                () -> fixture.click("/item_lore --operation remove --line 1 --internal 1"));
        assertInstanceOf(NoPermissionException.class, exception.getCause());
        assertEquals(List.of("first", "second"), fixture.lines());
        verifyNoInteractions(fixture.platform);
    }

    @Test
    void commandClicksUseTheExistingLineBounds() {
        Fixture fixture = new Fixture();
        fixture.hold("first", "second");
        fixture.execute("");
        fixture.click("/item_lore --operation up --line 1 --internal 1");
        fixture.assertFeedback(MessageConstants.COMMAND_ITEM_LORE_BOUND);
        assertEquals(List.of("first", "second"), fixture.lines());
    }

    @Test
    void doesNotRegisterAdditionalListeners() {
        Fixture fixture = new Fixture();
        fixture.command.registerRelatedFunctions();
        fixture.command.unregisterRelatedFunctions();
        verifyNoInteractions(fixture.pluginManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"item_lore", "sparrow item_lore", "custom tools lore"})
    @SuppressWarnings("unchecked")
    void commandPacketAllowsLoreClicksWithoutRelaxingSiblingCommands(String usage) throws Exception {
        Fixture fixture = new Fixture(usage);
        String rootName = usage.split(" ")[0];
        fixture.manager.command(fixture.manager.commandBuilder(rootName).literal("restricted")
                .senderType(Player.class).permission("sparrow.command.other").handler(context -> {}));
        CloudBrigadierManager<CommandSender, CommandSourceStack> brigadier = new CloudBrigadierManager<>(fixture.manager,
                SenderMapper.create(CommandSourceStack::getBukkitSender, sender -> {
                    CommandSourceStack source = mock(CommandSourceStack.class);
                    when(source.getBukkitSender()).thenReturn(sender);
                    return source;
                }));
        RootCommandNode<CommandSourceStack> root = new RootCommandNode<>();
        root.addChild(brigadier.literalBrigadierNodeFactory().createNode(rootName,
                fixture.manager.commandTree().getNamedNode(rootName), context -> 1,
                (sender, permission) -> fixture.manager.testPermission(sender, permission).allowed()));
        var field = Commands.class.getDeclaredField("COMMAND_NODE_INSPECTOR");
        field.setAccessible(true);
        var inspector = (ClientboundCommandsPacket.NodeInspector<CommandSourceStack>) field.get(null);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundCommandsPacket decoded;
        try {
            ClientboundCommandsPacket.STREAM_CODEC.encode(buffer, new ClientboundCommandsPacket(root, inspector));
            decoded = ClientboundCommandsPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
        var clientRoot = decoded.getRoot(mock(CommandBuildContext.class), new ClientboundCommandsPacket.NodeBuilder<Boolean>() {
            @Override
            public ArgumentBuilder<Boolean, ?> createLiteral(String id) {
                return LiteralArgumentBuilder.literal(id);
            }

            @Override
            public ArgumentBuilder<Boolean, ?> createArgument(String id, ArgumentType<?> type, Identifier suggestionId) {
                return RequiredArgumentBuilder.argument(id, type);
            }

            @Override
            public ArgumentBuilder<Boolean, ?> configure(ArgumentBuilder<Boolean, ?> builder, boolean executable, boolean restricted) {
                if (executable) {
                    builder.executes(context -> 1);
                }
                return builder.requires(allowRestricted -> allowRestricted || !restricted);
            }
        });
        CommandDispatcher<Boolean> client = new CommandDispatcher<>(clientRoot);
        var parsed = client.parse(usage + " --operation down --line 1 --internal 1", false);
        assertFalse(parsed.getReader().canRead());
        assertTrue(parsed.getExceptions().isEmpty());
        assertNotNull(parsed.getContext().getLastChild().getCommand());
        assertTrue(client.parse(rootName + " restricted", false).getReader().canRead());
        assertFalse(client.parse(rootName + " restricted", true).getReader().canRead());
        if (usage.contains(" ")) {
            assertTrue(client.parse(rootName + " earlier", false).getReader().canRead());
            assertFalse(client.parse(rootName + " earlier", true).getReader().canRead());
        }
        fixture.allowed = false;
        assertFalse(root.getChild(rootName).canUse(brigadier.senderMapper().reverse(fixture.player)));
        assertFalse(root.getChild(rootName).canUse(brigadier.senderMapper().reverse(mock(ConsoleCommandSender.class))));
    }

    private static final class Fixture {
        private final CraftPlayer player = mock(CraftPlayer.class);
        private final Inventory inventory = mock(Inventory.class);
        private final PlatformExecutor platform = mock(PlatformExecutor.class);
        private final CommandManager feedback = mock(CommandManager.class);
        private final String name;
        private final ItemLoreCommand command;
        private final PluginManager pluginManager = mock(PluginManager.class);
        private boolean allowed = true;
        private ItemStack held = ItemStack.EMPTY;
        private MetadataValue metadata;
        private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(CommandSender sender, String permission) {
                return Fixture.this.allowed;
            }
        };

        private Fixture() {
            this("item_lore");
        }

        private Fixture(String name) {
            this.name = name;
            SparrowPlugin plugin = mock(SparrowPlugin.class);
            PlayerManager players = mock(PlayerManager.class);
            BukkitSparrowPlayer sparrowPlayer = mock(BukkitSparrowPlayer.class);
            when(plugin.playerManager()).thenReturn(players);
            when(players.getPlayer(this.player)).thenReturn(sparrowPlayer);
            SchedulerAdapter scheduler = mock(SchedulerAdapter.class);
            when(plugin.scheduler()).thenReturn(scheduler);
            JavaPlugin javaPlugin = mock(JavaPlugin.class);
            Server server = mock(Server.class);
            when(plugin.javaPlugin()).thenReturn(javaPlugin);
            when(javaPlugin.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(this.pluginManager);
            when(this.feedback.getCommandManager()).thenReturn(this.manager);
            when(scheduler.platform()).thenReturn(this.platform);
            when(sparrowPlayer.getItemInMainHand()).thenAnswer(ignored -> this.inventory.getSelectedItem());
            when(this.inventory.getSelectedItem()).thenAnswer(ignored -> this.held);
            when(this.player.getMetadata("sparrow_lore")).thenAnswer(ignored -> this.metadata == null ? List.of() : List.of(this.metadata));
            doAnswer(invocation -> {
                this.metadata = invocation.getArgument(1);
                return null;
            }).when(this.player).setMetadata(eq("sparrow_lore"), any());
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(this.platform).run(any(Runnable.class), any(Runnable.class), same(this.player));
            this.command = new ItemLoreCommand(this.feedback, plugin);
            this.command.setCommandConfig(new CommandConfig(true, List.of("/" + name), "sparrow.command.item-lore"));
            String[] literals = name.split(" ");
            if (literals.length > 1) {
                this.manager.command(this.manager.commandBuilder(literals[0]).literal("earlier")
                        .senderType(Player.class).permission("sparrow.command.other").handler(context -> {}));
            }
            Command.Builder<CommandSender> builder = Command.<CommandSender>newBuilder(literals[0], CommandMeta.empty()).permission("sparrow.command.item-lore");
            for (int i = 1; i < literals.length; i++) {
                builder = builder.literal(literals[i]);
            }
            this.command.registerCommand(this.manager, builder);
        }

        private void click(String command) {
            this.manager.commandExecutor().executeCommand(this.player, command.substring(1)).join();
        }

        private ItemStack hold(String... lines) {
            this.held = new ItemStack(Items.STONE);
            if (lines.length > 0) {
                this.held.set(DataComponents.LORE, new ItemLore(Arrays.stream(lines).map(net.minecraft.network.chat.Component::literal).map(value -> (net.minecraft.network.chat.Component) value).toList()));
            }
            return this.held;
        }

        private void execute(String flags) {
            this.manager.commandExecutor().executeCommand(this.player, this.name + " " + flags).join();
            verify(this.inventory, never()).setSelectedItem(any());
        }

        private List<String> lines() {
            return this.held.get(DataComponents.LORE).lines().stream().map(net.minecraft.network.chat.Component::getString).toList();
        }

        private void assertFeedback(TranslatableComponent.Builder message) {
            verify(this.feedback).handleCommandFeedback(eq(this.player), same(message), any(Component[].class));
        }

        private List<ClickEvent> clicks(TranslatableComponent.Builder message) {
            ArgumentCaptor<Component[]> args = ArgumentCaptor.forClass(Component[].class);
            verify(this.feedback).handleCommandFeedback(eq(this.player), same(message), args.capture());
            ArrayDeque<Component> remaining = new ArrayDeque<>(List.of(args.getValue()));
            List<ClickEvent> clicks = new ArrayList<>();
            while (!remaining.isEmpty()) {
                Component component = remaining.removeFirst();
                if (component.clickEvent() != null) {
                    clicks.add(component.clickEvent());
                }
                remaining.addAll(component.children());
            }
            return clicks;
        }
    }
}
