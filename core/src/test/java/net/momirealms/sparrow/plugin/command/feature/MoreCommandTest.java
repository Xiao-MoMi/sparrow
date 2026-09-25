package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.scheduler.SchedulerAdapter;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MoreCommandTest {
    @BeforeAll
    static void initializeMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void fillsTheSelectedStackInPlaceUsingTheMaterialLimit() {
        Fixture fixture = new Fixture();
        ItemStack item = new ItemStack(Items.STONE, 7);
        item.set(DataComponents.MAX_STACK_SIZE, 16);
        fixture.inventory.setSelectedSlot(4);
        fixture.inventory.setSelectedItem(item);
        fixture.execute("more");
        assertSame(item, fixture.inventory.getSelectedItem());
        assertEquals(64, item.getCount());
        assertEquals(16, item.get(DataComponents.MAX_STACK_SIZE));
        assertTrue(fixture.inventory.getItem(0).isEmpty());
        verify(fixture.receiver, never()).dropItem(any());
    }

    @Test
    void extraStacksAreIndependentAndDoNotChangeTheHeldItem() {
        Fixture fixture = new Fixture();
        ItemStack item = new ItemStack(Items.ENDER_PEARL, 7);
        item.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("keep"));
        fixture.inventory.setSelectedItem(item);
        fixture.execute("more 35");
        ArgumentCaptor<org.bukkit.inventory.ItemStack> drops = ArgumentCaptor.forClass(org.bukkit.inventory.ItemStack.class);
        verify(fixture.receiver, times(3)).dropItem(drops.capture());
        List<ItemStack> stacks = drops.getAllValues().stream().map(stack -> ((CraftItemStack) stack).handle).toList();
        assertEquals(List.of(16, 16, 3), stacks.stream().map(ItemStack::getCount).toList());
        for (int i = 0; i < stacks.size(); i++) {
            assertNotSame(item, stacks.get(i));
            assertEquals(item.get(DataComponents.CUSTOM_NAME), stacks.get(i).get(DataComponents.CUSTOM_NAME));
        }
        stacks.getFirst().setCount(1);
        assertEquals(16, stacks.get(1).getCount());
        assertEquals(7, item.getCount());
        assertSame(item, fixture.inventory.getSelectedItem());
    }

    @Test
    void emptyAndFullHandsAreUnchanged() {
        Fixture fixture = new Fixture();
        fixture.execute("more");
        assertTrue(fixture.inventory.getSelectedItem().isEmpty());
        ItemStack full = new ItemStack(Items.STONE, 64);
        fixture.inventory.setSelectedItem(full);
        fixture.execute("more");
        assertSame(full, fixture.inventory.getSelectedItem());
        assertEquals(64, full.getCount());
        verify(fixture.feedback, times(2)).handleCommandFeedback(eq(fixture.player), same(MessageConstants.COMMAND_MORE_NO_CHANGE), any(Component[].class));
        verify(fixture.receiver, never()).dropItem(any());
    }

    private static final class Fixture {
        private final CraftPlayer player = mock(CraftPlayer.class);
        private final ServerPlayer handle = mock(ServerPlayer.class);
        private final Inventory inventory = new Inventory(this.handle, new EntityEquipment());
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
            when(this.receiver.getItemInMainHand()).thenAnswer(ignored -> this.inventory.getSelectedItem());
            when(this.player.getHandle()).thenReturn(this.handle);
            when(this.handle.getInventory()).thenReturn(this.inventory);
            when(this.player.getName()).thenReturn("Tester");
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(platform).run(any(Runnable.class), any(Runnable.class), same(this.player));
            new MoreCommand(this.feedback, plugin).registerCommand(this.manager, Command.newBuilder("more", CommandMeta.empty()));
        }

        private void execute(String input) {
            this.manager.commandExecutor().executeCommand(this.player, input).join();
        }
    }
}
