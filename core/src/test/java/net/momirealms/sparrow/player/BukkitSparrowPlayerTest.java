package net.momirealms.sparrow.player;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
