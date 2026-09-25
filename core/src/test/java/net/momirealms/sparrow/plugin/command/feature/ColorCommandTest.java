package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.scheduler.SchedulerAdapter;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.entity.CraftPlayer;
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

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

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
        assertSame(original, changed);
        fixture.hold(changed);
        clearInvocations(fixture.handle);
        fixture.execute("color red");
        assertEquals(0xff5555, fixture.written().get(DataComponents.DYED_COLOR).rgb());
    }

    @Test
    void queriesColorWithoutWritingAndProvidesCopyableHex() {
        Fixture fixture = new Fixture();
        ItemStack original = new ItemStack(Items.STONE);
        original.set(DataComponents.DYED_COLOR, new DyedItemColor(0x123456));
        fixture.hold(original);
        ItemStack beforeQuery = original.copy();
        fixture.execute("color");
        assertTrue(ItemStack.matches(beforeQuery, original));
        ArgumentCaptor<Component[]> args = ArgumentCaptor.forClass(Component[].class);
        verify(fixture.feedback).handleCommandFeedback(eq(fixture.player), same(MessageConstants.COMMAND_COLOR_QUERY), args.capture());
        assertEquals(ClickEvent.copyToClipboard("#123456"), args.getValue()[1].clickEvent());
        verify(fixture.handle, never()).setItemSlot(any(), any());
    }

    @Test
    void reportsMissingComponentWithoutAddingADefault() {
        Fixture fixture = new Fixture();
        fixture.hold(new ItemStack(Items.STONE));
        fixture.execute("color");
        fixture.assertFeedback(MessageConstants.COMMAND_COLOR_MISSING);
        assertNull(fixture.items.get(EquipmentSlot.HAND).get(DataComponents.DYED_COLOR));
        verify(fixture.handle, never()).setItemSlot(any(), any());
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
        verify(fixture.handle, never()).setItemSlot(any(), any());
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
        fixture.items.put(slot, original);
        String flag = " --slot " + slot.name().toLowerCase(Locale.ROOT);
        fixture.execute("color #abcdef" + flag);
        ItemStack changed = fixture.written(slot);
        assertEquals(0xabcdef, changed.get(DataComponents.DYED_COLOR).rgb());
        assertEquals(3, changed.getCount());
        assertSame(original, changed);
        verify(fixture.handle).getItemBySlot(CraftEquipmentSlot.getNMS(slot));
        verifyNoMoreInteractions(fixture.handle);

        clearInvocations(fixture.handle, fixture.feedback);
        fixture.items.put(slot, changed);
        fixture.execute("color" + flag);
        fixture.assertFeedback(MessageConstants.COMMAND_COLOR_QUERY);
        verify(fixture.handle).getItemBySlot(CraftEquipmentSlot.getNMS(slot));
        verifyNoMoreInteractions(fixture.handle);
    }

    private static final class Fixture {
        private final CraftPlayer player = mock(CraftPlayer.class);
        private final ServerPlayer handle = mock(ServerPlayer.class);
        private final Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
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
            PlayerManager players = mock(PlayerManager.class);
            BukkitSparrowPlayer sparrowPlayer = mock(BukkitSparrowPlayer.class);
            when(plugin.playerManager()).thenReturn(players);
            when(players.getPlayer(this.player)).thenReturn(sparrowPlayer);
            SchedulerAdapter scheduler = mock(SchedulerAdapter.class);
            when(plugin.scheduler()).thenReturn(scheduler);
            when(scheduler.platform()).thenReturn(this.platform);
            when(sparrowPlayer.nmsPlayer()).thenReturn(this.handle);
            when(this.handle.getItemBySlot(any())).thenAnswer(invocation -> this.items.getOrDefault(CraftEquipmentSlot.getSlot(invocation.getArgument(0)), ItemStack.EMPTY));
            when(this.player.getName()).thenReturn("Tester");
            doAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return null;
            }).when(this.platform).run(any(Runnable.class), any(Runnable.class), same(this.player));
            ColorCommand command = new ColorCommand(this.feedback, plugin);
            command.registerCommand(this.manager, Command.newBuilder("color", CommandMeta.empty()));
        }

        private void hold(ItemStack item) {
            this.items.put(EquipmentSlot.HAND, item);
        }

        private void execute(String input) {
            this.manager.commandExecutor().executeCommand(this.player, input).join();
        }

        private ItemStack written() {
            return this.written(EquipmentSlot.HAND);
        }

        private ItemStack written(EquipmentSlot slot) {
            verify(this.handle, never()).setItemSlot(any(), any());
            return this.items.get(slot);
        }

        private void assertFeedback(TranslatableComponent.Builder message) {
            verify(this.feedback).handleCommandFeedback(eq(this.player), same(message), any(Component[].class));
        }
    }
}
