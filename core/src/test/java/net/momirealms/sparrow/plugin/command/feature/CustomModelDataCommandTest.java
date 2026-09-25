package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.scheduler.SchedulerAdapter;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.incendo.cloud.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomModelDataCommandTest {
    @BeforeAll
    static void initializeMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "123.0", "123.5", "-4", "-4.5"})
    void addsNumericDataToAnOrdinaryItem(String input) {
        Fixture fixture = new Fixture();
        ItemStack original = new ItemStack(Items.STONE, 7);
        fixture.hold(original);
        fixture.execute("custom_model_data " + input);
        ItemStack changed = fixture.written();
        assertEquals(List.of(Float.parseFloat(input)), changed.get(DataComponents.CUSTOM_MODEL_DATA).floats());
        assertEquals(7, changed.getCount());
        assertSame(original, changed);
        fixture.assertFeedback(MessageConstants.COMMAND_CUSTOM_MODEL_DATA_SUCCESS);
    }

    @Test
    void changesOnlyTheFirstFloatAndPreservesOtherComponents() {
        Fixture fixture = new Fixture();
        ItemStack original = new ItemStack(Items.STONE);
        original.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("keep"));
        CustomModelData data = new CustomModelData(List.of(1.0f, 2.5f), List.of(true), List.of("keep"), List.of(0xabcdef));
        original.set(DataComponents.CUSTOM_MODEL_DATA, data);
        fixture.hold(original);
        fixture.execute("custom_model_data 3.5");
        ItemStack changed = fixture.written();
        assertEquals(new CustomModelData(List.of(3.5f, 2.5f), data.flags(), data.strings(), data.colors()), changed.get(DataComponents.CUSTOM_MODEL_DATA));
        assertEquals(original.get(DataComponents.CUSTOM_NAME), changed.get(DataComponents.CUSTOM_NAME));
        assertSame(original, changed);
        clearInvocations(fixture.inventory, fixture.feedback);
        fixture.hold(changed);
        ItemStack beforeQuery = changed.copy();
        fixture.execute("custom_model_data");
        fixture.assertFeedback(MessageConstants.COMMAND_CUSTOM_MODEL_DATA_QUERY);
        assertTrue(ItemStack.matches(beforeQuery, changed));
        verify(fixture.inventory, never()).setSelectedItem(any());
    }

    @Test
    void missingDataAndEmptyHandDoNotWrite() {
        Fixture fixture = new Fixture();
        fixture.hold(new ItemStack(Items.STONE));
        fixture.execute("custom_model_data");
        fixture.assertFeedback(MessageConstants.COMMAND_CUSTOM_MODEL_DATA_MISSING);
        assertNull(fixture.held.get(DataComponents.CUSTOM_MODEL_DATA));
        clearInvocations(fixture.feedback);
        fixture.hold(ItemStack.EMPTY);
        fixture.execute("custom_model_data 1");
        fixture.assertFeedback(MessageConstants.COMMAND_CUSTOM_MODEL_DATA_ITEMLESS);
        verify(fixture.inventory, never()).setSelectedItem(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"text", "[1,2]", "1 2", "1f", "1e3", "NaN", "2147483648"})
    void rejectsUnsupportedInputBeforeAccessingInventory(String input) {
        Fixture fixture = new Fixture();
        assertThrows(RuntimeException.class, () -> fixture.execute("custom_model_data " + input));
        verifyNoInteractions(fixture.platform, fixture.inventory);
    }

    private static final class Fixture {
        private final CraftPlayer player = mock(CraftPlayer.class);
        private final Inventory inventory = mock(Inventory.class);
        private final PlatformExecutor platform = mock(PlatformExecutor.class);
        private final CommandManager feedback = mock(CommandManager.class);
        private ItemStack held;
        private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(CommandSender sender, String permission) {
                return true;
            }
        };

        private Fixture() {
            SparrowPlugin plugin = mock(SparrowPlugin.class);
            PlayerManager players = mock(PlayerManager.class);
            BukkitSparrowPlayer sparrowPlayer = mock(BukkitSparrowPlayer.class);
            when(plugin.playerManager()).thenReturn(players);
            when(players.getPlayer(this.player)).thenReturn(sparrowPlayer);
            SchedulerAdapter scheduler = mock(SchedulerAdapter.class);
            when(plugin.scheduler()).thenReturn(scheduler);
            when(scheduler.platform()).thenReturn(this.platform);
            when(sparrowPlayer.getItemInMainHand()).thenAnswer(ignored -> this.inventory.getSelectedItem());
            when(this.player.getName()).thenReturn("Tester");
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(this.platform).run(any(Runnable.class), any(Runnable.class), same(this.player));
            CustomModelDataCommand command = new CustomModelDataCommand(this.feedback, plugin);
            command.registerCommand(this.manager, Command.newBuilder("custom_model_data", CommandMeta.empty()));
        }

        private void hold(ItemStack item) {
            this.held = item;
            when(this.inventory.getSelectedItem()).thenReturn(item);
        }

        private void execute(String input) {
            this.manager.commandExecutor().executeCommand(this.player, input).join();
        }

        private ItemStack written() {
            verify(this.inventory, never()).setSelectedItem(any());
            return this.held;
        }

        private void assertFeedback(TranslatableComponent.Builder message) {
            verify(this.feedback).handleCommandFeedback(eq(this.player), same(message), any(Component[].class));
        }
    }
}
