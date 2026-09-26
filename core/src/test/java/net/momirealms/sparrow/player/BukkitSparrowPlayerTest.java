package net.momirealms.sparrow.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitSparrowPlayerTest {
    @BeforeAll
    static void initializeMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void cachesThePlayerButReadsTheCurrentSelectedStack() {
        CraftPlayer platform = mock(CraftPlayer.class);
        ServerPlayer handle = mock(ServerPlayer.class);
        Inventory inventory = new Inventory(handle, new EntityEquipment());
        when(platform.getHandle()).thenReturn(handle);
        when(handle.getInventory()).thenReturn(inventory);
        SparrowPlayer player = new BukkitSparrowPlayer(mock(PlayerConnection.class), platform);
        assertSame(handle, player.nmsPlayer());
        assertTrue(player.getItemInMainHand().isEmpty());

        ItemStack first = new ItemStack(Items.STONE);
        ItemStack second = new ItemStack(Items.DIRT);
        inventory.setItem(0, first);
        inventory.setItem(4, second);
        assertSame(first, player.getItemInMainHand());
        inventory.setSelectedSlot(4);
        assertSame(second, player.getItemInMainHand());
        CustomModelData data = new CustomModelData(List.of(1.0f), List.of(), List.of(), List.of());
        player.getItemInMainHand().set(DataComponents.CUSTOM_MODEL_DATA, data);
        assertSame(data, inventory.getItem(4).get(DataComponents.CUSTOM_MODEL_DATA));
        assertNull(first.get(DataComponents.CUSTOM_MODEL_DATA));
        inventory.setSelectedItem(ItemStack.EMPTY);
        assertTrue(player.getItemInMainHand().isEmpty());
        verify(platform).getHandle();
        verifyNoMoreInteractions(platform);
    }

    @Test
    @SuppressWarnings("deprecation")
    void kicksWithLegacyTextKeepingRgbColors() {
        CraftPlayer platform = mock(CraftPlayer.class);
        when(platform.getHandle()).thenReturn(mock(ServerPlayer.class));
        SparrowPlayer player = new BukkitSparrowPlayer(mock(PlayerConnection.class), platform);
        player.kick(Component.text("bye", TextColor.color(0xFFB6CB)));
        verify(platform).kickPlayer("§x§f§f§b§6§c§bbye");
    }

    @Test
    void sendsBossBarAddAndRemovePackets() {
        CraftPlayer platform = mock(CraftPlayer.class);
        when(platform.getHandle()).thenReturn(mock(ServerPlayer.class));
        PlayerConnection connection = mock(PlayerConnection.class);
        SparrowPlayer player = new BukkitSparrowPlayer(connection, platform);
        UUID id = UUID.randomUUID();
        // 组件转换需要注册表
        try (MockedStatic<CraftRegistry> registry = mockStatic(CraftRegistry.class)) {
            registry.when(CraftRegistry::getMinecraftRegistry).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            player.showBossBar(id, Component.text("maintenance"), 0.5f, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        player.hideBossBar(id);

        ArgumentCaptor<Object> packets = ArgumentCaptor.forClass(Object.class);
        verify(connection, times(2)).sendPacket(packets.capture());
        List<String> received = new ArrayList<>();
        for (Object packet : packets.getAllValues()) {
            ((ClientboundBossEventPacket) packet).dispatch(new ClientboundBossEventPacket.Handler() {
                @Override
                public void add(UUID bossBarId, net.minecraft.network.chat.Component name, float progress, BossEvent.BossBarColor color,
                                BossEvent.BossBarOverlay overlay, boolean darkenScreen, boolean playMusic, boolean createWorldFog) {
                    assertEquals(id, bossBarId);
                    assertEquals("maintenance", name.getString());
                    assertEquals(0.5f, progress);
                    assertEquals(BossEvent.BossBarColor.RED, color);
                    assertEquals(BossEvent.BossBarOverlay.NOTCHED_10, overlay);
                    received.add("add");
                }

                @Override
                public void remove(UUID bossBarId) {
                    assertEquals(id, bossBarId);
                    received.add("remove");
                }
            });
        }
        assertEquals(List.of("add", "remove"), received);
    }
}
