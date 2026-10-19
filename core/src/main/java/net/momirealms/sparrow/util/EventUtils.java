package net.momirealms.sparrow.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

public final class EventUtils {
    private EventUtils() {}

    public static void fireAndForget(Event event) {
        Bukkit.getPluginManager().callEvent(event);
    }

    public static boolean fireAndCheckCancel(Event event) {
        if (!(event instanceof Cancellable cancellable))
            throw new IllegalArgumentException("Only cancellable events are allowed here");
        Bukkit.getPluginManager().callEvent(event);
        return cancellable.isCancelled();
    }

    @SuppressWarnings("deprecation")
    public static Location getInteractionPoint(PlayerInteractEvent event) {
        Vector clickedPosition = event.getClickedPosition();
        if (clickedPosition == null) {
            return null;
        }
        return event.getClickedBlock().getLocation().add(clickedPosition);
    }
}
