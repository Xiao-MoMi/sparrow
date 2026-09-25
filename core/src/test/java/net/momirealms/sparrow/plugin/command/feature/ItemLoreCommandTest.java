package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.scheduler.SchedulerAdapter;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.Command;
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
        fixture.hold("first", "second");
        fixture.execute("");
        List<ClickEvent> clicks = fixture.clicks(MessageConstants.COMMAND_ITEM_LORE_QUERY);
        assertTrue(clicks.contains(ClickEvent.runCommand("/lore_alias --operation remove --line 1 --internal 1")));
        assertTrue(clicks.contains(ClickEvent.runCommand("/lore_alias --operation down --line 1 --internal 1")));
        assertTrue(clicks.contains(ClickEvent.runCommand("/lore_alias --operation up --line 2 --internal 1")));
        assertFalse(clicks.stream().anyMatch(click -> click.value().contains("up --line 1")));
        assertFalse(clicks.stream().anyMatch(click -> click.value().contains("down --line 2")));
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

    private static final class Fixture {
        private final CraftPlayer player = mock(CraftPlayer.class);
        private final ServerPlayer handle = mock(ServerPlayer.class);
        private final Inventory inventory = mock(Inventory.class);
        private final PlatformExecutor platform = mock(PlatformExecutor.class);
        private final CommandManager feedback = mock(CommandManager.class);
        private final String name;
        private ItemStack held = ItemStack.EMPTY;
        private MetadataValue metadata;
        private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(CommandSender sender, String permission) {
                return true;
            }
        };

        private Fixture() {
            this("item_lore");
        }

        private Fixture(String name) {
            this.name = name;
            SparrowPlugin plugin = mock(SparrowPlugin.class);
            SchedulerAdapter scheduler = mock(SchedulerAdapter.class);
            when(plugin.scheduler()).thenReturn(scheduler);
            when(plugin.javaPlugin()).thenReturn(mock(JavaPlugin.class));
            when(scheduler.platform()).thenReturn(this.platform);
            when(this.player.getHandle()).thenReturn(this.handle);
            when(this.handle.getInventory()).thenReturn(this.inventory);
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
            ItemLoreCommand command = new ItemLoreCommand(this.feedback, plugin);
            command.setCommandConfig(new CommandConfig(true, List.of("/" + name), "sparrow.command.item-lore"));
            command.registerCommand(this.manager, Command.newBuilder(name, CommandMeta.empty()));
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
