package net.momirealms.sparrow.bukkit.user;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BukkitOnlineUser extends BukkitOfflineUser {

    BukkitOnlineUser(UUID uniqueId) {
        super(uniqueId);
    }

    @Nullable
    @Override
    public Player getPlayer() {
        return Bukkit.getPlayer(getUniqueId());
    }

    @Override
    public boolean isOnline() {
        return getPlayer() != null;
    }
}
