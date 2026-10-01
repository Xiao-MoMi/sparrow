package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
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
import org.bukkit.Bukkit;
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
import org.mockito.MockedStatic;

import java.util.EnumMap;
import java.util.Map;

import java.util.List;

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
    void emptyHandAndInvalidInputDoNotWrite() {
        Fixture fixture = new Fixture();
        fixture.hold(ItemStack.EMPTY);
        fixture.execute("color #ffffff");
        fixture.assertFeedback(MessageConstants.COMMAND_COLOR_ITEMLESS_SELF);
        clearInvocations(fixture.platform);
        assertThrows(RuntimeException.class, () -> fixture.execute("color"));
        assertThrows(RuntimeException.class, () -> fixture.execute("color #notacolor"));
        assertThrows(RuntimeException.class, () -> fixture.execute("color #ffffff Tester invalid"));
        verifyNoInteractions(fixture.platform);
        verify(fixture.handle, never()).setItemSlot(any(), any());
    }

    @Test
    void silentStillWritesColor() {
        Fixture fixture = new Fixture();
        fixture.hold(new ItemStack(Items.STONE));
        // flag 只能写在所有位置参数之后, 省略玩家时不能带 flag
        assertThrows(RuntimeException.class, () -> fixture.execute("color #000000 --silent"));
        fixture.execute("color #000000 Tester --silent");
        assertEquals(0, fixture.written().get(DataComponents.DYED_COLOR).rgb());
        verifyNoInteractions(fixture.feedback);
    }

    @Test
    void targetedColorOnlyChangesMainHandAndRejectsSlotArgument() {
        Fixture fixture = new Fixture();
        ItemStack original = new ItemStack(Items.STONE, 3);
        ItemStack offHand = new ItemStack(Items.STONE);
        fixture.hold(original);
        fixture.items.put(EquipmentSlot.OFF_HAND, offHand);
        fixture.execute("color #abcdef Tester");
        ItemStack changed = fixture.written();
        assertEquals(0xabcdef, changed.get(DataComponents.DYED_COLOR).rgb());
        assertEquals(3, changed.getCount());
        assertSame(original, changed);
        assertNull(offHand.get(DataComponents.DYED_COLOR));
        verify(fixture.handle).getItemBySlot(CraftEquipmentSlot.getNMS(EquipmentSlot.HAND));
        verifyNoMoreInteractions(fixture.handle);
        assertThrows(RuntimeException.class, () -> fixture.execute("color #abcdef Tester off_hand"));
    }

    @Test
    void selfColorNeedsOnlyBasePermissionButExplicitSelfRequiresOther() {
        Fixture fixture = new Fixture();
        fixture.otherAllowed = false;
        fixture.hold(new ItemStack(Items.STONE));
        fixture.execute("color #abcdef");
        assertEquals(0xabcdef, fixture.written().get(DataComponents.DYED_COLOR).rgb());
        clearInvocations(fixture.platform);
        assertThrows(RuntimeException.class, () -> fixture.execute("color #000000 Tester --silent"));
        verifyNoInteractions(fixture.platform);
        assertEquals(0xabcdef, fixture.written().get(DataComponents.DYED_COLOR).rgb());
    }

    private static final class Fixture {
        private boolean otherAllowed = true;
        private final CraftPlayer player = mock(CraftPlayer.class);
        private final ServerPlayer handle = mock(ServerPlayer.class);
        private final Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
        private final PlatformExecutor platform = mock(PlatformExecutor.class);
        private final CommandManager feedback = mock(CommandManager.class);
        private final org.incendo.cloud.CommandManager<CommandSender> manager = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
            @Override
            public boolean hasPermission(CommandSender sender, String permission) {
                return !permission.endsWith(".other") || Fixture.this.otherAllowed;
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
            command.setCommandConfig(new CommandConfig(true, List.of("/color"), "sparrow.command.color"));
            command.registerCommand(this.manager, Command.<CommandSender>newBuilder("color", CommandMeta.empty()).permission("sparrow.command.color"));
        }

        private void hold(ItemStack item) {
            this.items.put(EquipmentSlot.HAND, item);
        }

        // 解析玩家参数时按名称查找在线玩家
        private void execute(String input) {
            try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
                bukkit.when(() -> Bukkit.getPlayer("Tester")).thenReturn(this.player);
                this.manager.commandExecutor().executeCommand(this.player, input).join();
            }
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
