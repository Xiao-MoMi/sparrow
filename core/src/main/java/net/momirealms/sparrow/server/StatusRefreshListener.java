package net.momirealms.sparrow.server;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

final class StatusRefreshListener implements Listener {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    public volatile @Nullable String[] worlds;

    public void onServerLoaded() {
        this.worlds = Bukkit.getWorlds().stream().map(World::getName).sorted().toArray(String[]::new);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldLoad(@NotNull WorldLoadEvent event) {
        if (SparrowPlugin.instance().isInitializing()) return;
        String[] worlds = this.worlds;
        String[] updated = Arrays.copyOf(worlds, worlds.length + 1);
        updated[worlds.length] = event.getWorld().getName();
        Arrays.sort(updated);
        this.worlds = updated;
        this.plugin.serverManager().heartbeat();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(@NotNull WorldUnloadEvent event) {
        if (SparrowPlugin.instance().isInitializing()) return;
        String world = event.getWorld().getName();
        this.worlds = Arrays.stream(this.worlds).filter(name -> !name.equals(world)).toArray(String[]::new);
        this.plugin.serverManager().heartbeat();
    }
}