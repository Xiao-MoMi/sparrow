package net.momirealms.sparrow.teleport.processor;

import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerLevel;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportProcessor;
import net.momirealms.sparrow.teleport.TeleportSearch;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.WorldLocation;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class SafeLocationProcessor implements TeleportProcessor.Target {
    public static final SparrowKey TYPE = SparrowKey.sparrow("safe-location");

    private NearbyOptions nearby = new NearbyOptions();
    private int maxChecks = 10_000;
    private boolean allowNetherRoof = false;
    private boolean forceOnFailure = false;
    private int fallbackMinY = -63;

    @Override
    public void validate() {
        if (this.nearby.horizontalRadius < 0) throw new IllegalArgumentException("safe-location nearby.horizontal-radius must be non-negative");
        if (this.nearby.verticalRadius < 0) throw new IllegalArgumentException("safe-location nearby.vertical-radius must be non-negative");
        if (this.maxChecks <= 0) throw new IllegalArgumentException("safe-location max-checks must be positive");
    }

    @NotNull
    @Override
    public CompletableFuture<Component> destination(@NotNull SparrowPlayer player, @NotNull Teleport teleport) {
        WorldLocation origin = teleport.location();
        Location location = origin.resolve();
        // 无效落点由传送服务返回 INVALID
        if (location == null) return PASS;
        ServerLevel world = ((CraftWorld) location.getWorld()).getHandle();
        TeleportSearch search = new TeleportSearch(world, teleport.dimensions(), this.allowNetherRoof, this.maxChecks);
        CompletableFuture<WorldLocation> searching = search.find(origin, this.nearby.horizontalRadius, this.nearby.verticalRadius);
        CompletableFuture<Component> result = searching.thenApply(found -> {
            if (found != null) {
                teleport.location(found);
                return null;
            }
            if (!this.forceOnFailure) return MessageConstants.TELEPORT_REJECTED_UNSAFE;
            double y = Math.max(origin.y(), Math.max(this.fallbackMinY, world.getMinY() + 1));
            teleport.location(new WorldLocation(origin.world(), origin.x(), y, origin.z(), origin.yaw(), origin.pitch()));
            return null;
        });
        result.whenComplete((reason, error) -> {
            if (result.isCancelled()) searching.cancel(false);
        });
        return result;
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class NearbyOptions {
        private int horizontalRadius = 8;
        private int verticalRadius = 16;
    }
}