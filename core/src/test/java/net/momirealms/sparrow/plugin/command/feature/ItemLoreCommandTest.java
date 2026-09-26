package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.PluginManager;
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
    void removingTheLastLineRemovesTheComponentAndOffersInsertion() {
        Fixture fixture = new Fixture();
        fixture.hold("only");
        fixture.execute("--operation remove --line 1");
        assertNull(fixture.held.get(DataComponents.LORE));
        assertEquals(List.of(ClickEvent.suggestCommand("/item_lore --operation insert --line 1 --lore ")), fixture.clicks(MessageConstants.COMMAND_ITEM_LORE_SUCCESS));
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
    void doesNotRegisterAdditionalListeners() {
        Fixture fixture = new Fixture();
        fixture.command.registerRelatedFunctions();
        fixture.command.unregisterRelatedFunctions();
        verifyNoInteractions(fixture.pluginManager);
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
