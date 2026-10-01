package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.CommandConfig;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HatCommandTest {
    @BeforeAll
    static void initializeMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void wearsTheLastItemAndHandsBackTheOldHelmet() {
        Fixture fixture = new Fixture();
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        fixture.equipment.set(EquipmentSlot.HEAD, helmet);
        fixture.inventory.setSelectedItem(new ItemStack(Items.CARVED_PUMPKIN));
        fixture.execute("hat");
        assertEquals(Items.CARVED_PUMPKIN, fixture.equipment.get(EquipmentSlot.HEAD).getItem());
        assertSame(helmet, fixture.inventory.getSelectedItem());
        verify(fixture.feedback).handleCommandFeedback(eq(fixture.player), same(MessageConstants.COMMAND_HAT_SUCCESS_SELF), any(Component[].class));
    }

    @Test
    void wearsOneItemAndStoresTheOldHelmetInTheInventory() {
        Fixture fixture = new Fixture();
        fixture.equipment.set(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        fixture.inventory.setSelectedSlot(4);
        fixture.inventory.setSelectedItem(new ItemStack(Items.CARVED_PUMPKIN, 5));
        fixture.execute("hat");
        ItemStack hat = fixture.equipment.get(EquipmentSlot.HEAD);
        assertEquals(Items.CARVED_PUMPKIN, hat.getItem());
        assertEquals(1, hat.getCount());
        assertEquals(4, fixture.inventory.getSelectedItem().getCount());
        assertEquals(Items.IRON_HELMET, fixture.inventory.getItem(0).getItem());
        verify(fixture.handle, never()).drop(any(ItemStack.class), anyBoolean());
    }

    @Test
    void dropsTheOldHelmetWhenTheInventoryIsFull() {
        Fixture fixture = new Fixture();
        fixture.equipment.set(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            fixture.inventory.setItem(i, new ItemStack(Items.STONE, 64));
        }
        fixture.inventory.setSelectedItem(new ItemStack(Items.CARVED_PUMPKIN, 5));
        fixture.execute("hat");
        assertEquals(Items.CARVED_PUMPKIN, fixture.equipment.get(EquipmentSlot.HEAD).getItem());
        verify(fixture.handle).drop(argThat((ItemStack stack) -> stack.is(Items.IRON_HELMET)), eq(false));
    }

    @Test
    void emptyHandLeavesTheHelmetUntouched() {
        Fixture fixture = new Fixture();
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        fixture.equipment.set(EquipmentSlot.HEAD, helmet);
        fixture.execute("hat");
        assertSame(helmet, fixture.equipment.get(EquipmentSlot.HEAD));
        verify(fixture.feedback).handleCommandFeedback(eq(fixture.player), same(MessageConstants.COMMAND_HAT_ITEMLESS_SELF), any(Component[].class));
    }

    private static final class Fixture {
        private final CraftPlayer player = mock(CraftPlayer.class);
        private final ServerPlayer handle = mock(ServerPlayer.class);
        private final EntityEquipment equipment = new EntityEquipment();
        private final Inventory inventory = new Inventory(this.handle, this.equipment);
        private final BukkitSparrowPlayer receiver = mock(BukkitSparrowPlayer.class);
        private final CommandManager feedback = mock(CommandManager.class);
        private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(CommandSender sender, String permission) {
                return true;
            }
        };

        private Fixture() {
            SparrowPlugin plugin = mock(SparrowPlugin.class);
            PlayerManager players = mock(PlayerManager.class);
            SchedulerAdapter scheduler = mock(SchedulerAdapter.class);
            PlatformExecutor platform = mock(PlatformExecutor.class);
            when(plugin.scheduler()).thenReturn(scheduler);
            when(scheduler.platform()).thenReturn(platform);
            when(plugin.playerManager()).thenReturn(players);
            when(players.getPlayer(this.player)).thenReturn(this.receiver);
            when(this.receiver.nmsPlayer()).thenReturn(this.handle);
            when(this.receiver.getItemInMainHand()).thenAnswer(ignored -> this.inventory.getSelectedItem());
            when(this.handle.getInventory()).thenReturn(this.inventory);
            when(this.handle.getItemBySlot(any())).thenAnswer(invocation -> this.equipment.get(invocation.getArgument(0)));
            doAnswer(invocation -> this.equipment.set(invocation.getArgument(0), invocation.getArgument(1))).when(this.handle).setItemSlot(any(), any());
            this.handle.connection = mock(ServerGamePacketListenerImpl.class);
            when(this.player.getName()).thenReturn("Tester");
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(platform).run(any(Runnable.class), any(Runnable.class), same(this.player));
            HatCommand command = new HatCommand(this.feedback, plugin);
            command.setCommandConfig(new CommandConfig(true, List.of("/hat"), "sparrow.command.hat"));
            command.registerCommand(this.manager, Command.newBuilder("hat", CommandMeta.empty()));
        }

        private void execute(String input) {
            this.manager.commandExecutor().executeCommand(this.player, input).join();
        }
    }
}
