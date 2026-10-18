package net.momirealms.sparrow.player;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class BukkitSparrowPlayer extends OfflineSparrowPlayer {
    public final PlayerConnection connection;
    public final Player platformPlayer;
    public final ServerPlayer nmsPlayer;

    BukkitSparrowPlayer(@NotNull PlayerConnection connection, @NotNull Player platformPlayer) {
        super(connection.uniqueId(), connection.name());
        this.connection = connection;
        this.platformPlayer = platformPlayer;
        this.nmsPlayer = ((CraftPlayer) platformPlayer).getHandle();
    }

    @NotNull
    @Override
    public Locale locale() {
        return this.nmsPlayer.adventure$locale;
    }

    @NotNull
    @Override
    public String getCurrentServerId() {
        return ServerConfig.serverId();
    }

    public void dropItem(@NotNull ItemStack stack) {
        Player player = this.platformPlayer;
        Location location = player.getLocation();
        Item item = player.getWorld().dropItem(player.getEyeLocation().subtract(0, 0.3, 0), stack);
        item.setPickupDelay(0);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double spread = random.nextDouble(0.02);
        double angle = random.nextDouble(Math.PI * 2);
        Vector velocity = location.getDirection()
                .multiply(0.3)
                .setY(-Math.sin(Math.toRadians(location.getPitch())) * 0.3 + 0.1 + (random.nextDouble() - random.nextDouble()) * 0.1);
        velocity.add(new Vector(Math.cos(angle) * spread, 0, Math.sin(angle) * spread));
        item.setVelocity(velocity);
    }

    public void sendDemo() {
        this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.DEMO_EVENT, 0.0f));
    }

    public void sendCredits() {
        this.connection.sendPacket(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 1.0f));
    }

    public void sendDebugMarker(int x, int y, int z) {
        BlockPos position = new BlockPos(x, y, z);
        this.connection.sendPacket(new ClientboundGameTestHighlightPosPacket(position, position));
    }
}