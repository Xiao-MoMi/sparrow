package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.scheduler.SchedulerAdapter;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.incendo.cloud.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ColorCommandTest {
    @BeforeAll
    static void initializeMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void addsAndOverwritesColorOnStoneWhilePreservingOtherComponents() {
        Fixture fixture = new Fixture();
        ItemStack original = new ItemStack(Items.STONE, 7);
        original.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("keep"));
        fixture.hold(original);
        fixture.execute("color #123456");
        ItemStack changed = fixture.written();
        assertEquals(0x123456, changed.get(DataComponents.DYED_COLOR).rgb());
        assertEquals(7, changed.getCount());
        assertEquals(original.get(DataComponents.CUSTOM_NAME), changed.get(DataComponents.CUSTOM_NAME));
        assertNull(original.get(DataComponents.DYED_COLOR));
        fixture.hold(changed);
        clearInvocations(fixture.inventory);
        fixture.execute("color red");
        assertEquals(0xff5555, fixture.written().get(DataComponents.DYED_COLOR).rgb());
    }

    @Test
    void queriesColorWithoutWritingAndProvidesCopyableHex() {
        Fixture fixture = new Fixture();
        ItemStack original = new ItemStack(Items.STONE);
        original.set(DataComponents.DYED_COLOR, new DyedItemColor(0x123456));
        fixture.hold(original);
        fixture.execute("color");
        ArgumentCaptor<Component[]> args = ArgumentCaptor.forClass(Component[].class);
        verify(fixture.feedback).handleCommandFeedback(eq(fixture.player), same(MessageConstants.COMMAND_COLOR_QUERY), args.capture());
        assertEquals(ClickEvent.copyToClipboard("#123456"), args.getValue()[1].clickEvent());
        verify(fixture.inventory, never()).setItem(any(EquipmentSlot.class), any());
    }

    @Test
    void reportsMissingComponentWithoutAddingADefault() {
        Fixture fixture = new Fixture();
        fixture.hold(new ItemStack(Items.STONE));
        fixture.execute("color");
        fixture.assertFeedback(MessageConstants.COMMAND_COLOR_MISSING);
        verify(fixture.inventory, never()).setItem(any(EquipmentSlot.class), any());
    }

    @Test
    void emptyHandAndInvalidInputDoNotWrite() {
        Fixture fixture = new Fixture();
        fixture.hold(ItemStack.EMPTY);
        fixture.execute("color #ffffff");
        fixture.assertFeedback(MessageConstants.COMMAND_COLOR_ITEMLESS);
        clearInvocations(fixture.platform);
        assertThrows(RuntimeException.class, () -> fixture.execute("color #notacolor"));
        assertThrows(RuntimeException.class, () -> fixture.execute("color #ffffff --slot invalid"));
        verifyNoInteractions(fixture.platform);
        verify(fixture.inventory, never()).setItem(any(EquipmentSlot.class), any());
    }

    @Test
    void silentStillWritesColor() {
        Fixture fixture = new Fixture();
        fixture.hold(new ItemStack(Items.STONE));
        fixture.execute("color #000000 --silent");
        assertEquals(0, fixture.written().get(DataComponents.DYED_COLOR).rgb());
        verifyNoInteractions(fixture.feedback);
    }

    @ParameterizedTest
    @EnumSource(EquipmentSlot.class)
    void readsAndWritesOnlyTheSelectedSlot(EquipmentSlot slot) {
        Fixture fixture = new Fixture();
        ItemStack original = new ItemStack(Items.STONE, 3);
        when(fixture.inventory.getItem(slot)).thenReturn(CraftItemStack.asCraftMirror(original));
        String flag = " --slot " + slot.name().toLowerCase(Locale.ROOT);
        fixture.execute("color #abcdef" + flag);
        ItemStack changed = fixture.written(slot);
        assertEquals(0xabcdef, changed.get(DataComponents.DYED_COLOR).rgb());
        assertEquals(3, changed.getCount());
        assertNull(original.get(DataComponents.DYED_COLOR));
        verify(fixture.inventory).getItem(slot);
        verifyNoMoreInteractions(fixture.inventory);

        clearInvocations(fixture.inventory, fixture.feedback);
        when(fixture.inventory.getItem(slot)).thenReturn(CraftItemStack.asCraftMirror(changed));
        fixture.execute("color" + flag);
        fixture.assertFeedback(MessageConstants.COMMAND_COLOR_QUERY);
        verify(fixture.inventory).getItem(slot);
        verifyNoMoreInteractions(fixture.inventory);
    }

    private static final class Fixture {
        private final Player player = mock(Player.class);
        private final PlayerInventory inventory = mock(PlayerInventory.class);
        private final PlatformExecutor platform = mock(PlatformExecutor.class);
        private final CommandManager feedback = mock(CommandManager.class);
        private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(CommandSender sender, String permission) {
                return true;
            }
        };

        private Fixture() {
            SparrowPlugin plugin = mock(SparrowPlugin.class);
            SchedulerAdapter scheduler = mock(SchedulerAdapter.class);
            when(plugin.scheduler()).thenReturn(scheduler);
            when(scheduler.platform()).thenReturn(this.platform);
            when(this.player.getInventory()).thenReturn(this.inventory);
            when(this.player.getName()).thenReturn("Tester");
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(this.platform).run(any(Runnable.class), any(Runnable.class), same(this.player));
            ColorCommand command = new ColorCommand(this.feedback, plugin);
            command.registerCommand(this.manager, Command.newBuilder("color", CommandMeta.empty()));
        }

        private void hold(ItemStack item) {
            when(this.inventory.getItem(EquipmentSlot.HAND)).thenReturn(CraftItemStack.asCraftMirror(item));
        }

        private void execute(String input) {
            this.manager.commandExecutor().executeCommand(this.player, input).join();
        }

        private ItemStack written() {
            return this.written(EquipmentSlot.HAND);
        }

        private ItemStack written(EquipmentSlot slot) {
            ArgumentCaptor<org.bukkit.inventory.ItemStack> item = ArgumentCaptor.forClass(org.bukkit.inventory.ItemStack.class);
            verify(this.inventory).setItem(eq(slot), item.capture());
            return ((CraftItemStack) item.getValue()).handle;
        }

        private void assertFeedback(TranslatableComponent.Builder message) {
            verify(this.feedback).handleCommandFeedback(eq(this.player), same(message), any(Component[].class));
        }
    }
}
