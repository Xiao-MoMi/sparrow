package net.momirealms.sparrow.player.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.SharedConstants;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.world.WorldLocation;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeleportManagerTest {
    @Test
    void consumesOnceAndExpiresAtTenSeconds() {
        SharedConstants.tryDetectVersion();
        AtomicLong clock = new AtomicLong();
        TeleportManager manager = new TeleportManager(mock(SparrowPlugin.class), clock::get);
        UUID player = UUID.randomUUID();
        WorldLocation destination = new WorldLocation("world", -10.5, 64, 8.25, 90, -15);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            assertTrue(manager.prepare(player, destination));
            clock.set(TimeUnit.SECONDS.toNanos(9));
            assertEquals(destination, WorldLocation.from(manager.consumeSpawn(player)));
            assertNull(manager.consumeSpawn(player));
            assertTrue(manager.prepare(player, destination));
            clock.addAndGet(TimeUnit.SECONDS.toNanos(10));
            assertNull(manager.consumeSpawn(player));
        }
    }

    @Test
    void rejectsInvalidCoordinatesAndRechecksWorldOnArrival() {
        SharedConstants.tryDetectVersion();
        SparrowPlugin plugin = mock(SparrowPlugin.class);
        PlayerManager players = mock(PlayerManager.class);
        TranslationManager translations = mock(TranslationManager.class);
        BukkitSparrowPlayer receiver = mock(BukkitSparrowPlayer.class);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(plugin.playerManager()).thenReturn(players);
        when(plugin.translationManager()).thenReturn(translations);
        when(players.getPlayer(player)).thenReturn(receiver);
        Component feedback = Component.text("invalid");
        when(translations.render(any(TranslatableComponent.Builder.class), any())).thenReturn(feedback);
        TeleportManager manager = new TeleportManager(plugin);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            assertFalse(manager.prepare(uuid, new WorldLocation("missing", 0, 64, 0, 0, 0)));
            assertFalse(manager.prepare(uuid, new WorldLocation("world", Double.NaN, 64, 0, 0, 0)));
            assertFalse(manager.prepare(uuid, new WorldLocation("world", Double.POSITIVE_INFINITY, 64, 0, 0, 0)));
            assertTrue(manager.prepare(uuid, new WorldLocation("world", 1, 64, 2, 0, 0)));
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(null);
            assertNull(manager.consumeSpawn(uuid));
            manager.onJoin(player);
            verify(receiver).sendMessage(feedback);
            manager.onJoin(player);
            verify(receiver, times(1)).sendMessage(feedback);
        }
    }
}
