package net.momirealms.sparrow.player.listener;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface PlayerPreLoginListener {

    void onPreLogin(@NotNull AsyncPlayerPreLoginEvent event);
}