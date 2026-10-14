package net.momirealms.sparrow.teleport.processor.target;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.search.NearbyTeleportSearch;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public record FindSafeLocation(int horizontalRadius, int verticalRadius, int maxChecks, boolean allowNetherRoof) implements PositionProcessor {
    public static final SparrowKey TYPE = SparrowKey.sparrow("find-safe-location");

    public FindSafeLocation() {
        this(8, 16, 10_000, false);
    }

    public FindSafeLocation {
        if (horizontalRadius < 0 || verticalRadius < 0 || maxChecks <= 0) {
            throw new IllegalArgumentException("Safe location search requires non-negative radii and a positive check budget");
        }
    }

    @NotNull
    public static FindSafeLocation fromNbt(@NotNull CompoundTag tag) {
        return new FindSafeLocation(
                tag.getInt("horizontal_radius", 8),
                tag.getInt("vertical_radius", 16),
                tag.getInt("max_checks", 10_000),
                tag.getBoolean("allow_nether_roof")
        );
    }

    @NotNull
    @Override
    public SparrowKey type() {
        return TYPE;
    }

    @NotNull
    @Override
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("horizontal_radius", this.horizontalRadius);
        tag.putInt("vertical_radius", this.verticalRadius);
        tag.putInt("max_checks", this.maxChecks);
        tag.putBoolean("allow_nether_roof", this.allowNetherRoof);
        return tag;
    }

    @NotNull
    @Override
    public CompletableFuture<Result> process(@NotNull TeleportContext context, @NotNull WorldLocation location) {
        if (context.toNbt().getBoolean(TeleportContext.UNSAFE)) return CompletableFuture.completedFuture(Result.Accept.INSTANCE);
        Location resolved = location.resolve();
        if (resolved == null) return CompletableFuture.completedFuture(new Result.Reject(MessageConstants.TELEPORT_REJECTED_INVALID));
        NearbyTeleportSearch search = new NearbyTeleportSearch(((CraftWorld) resolved.getWorld()).getHandle(), context.getDimensions(), this.allowNetherRoof, this.maxChecks);
        CompletableFuture<WorldLocation> searching = search.find(location, this.horizontalRadius, this.verticalRadius);
        CompletableFuture<Result> result = searching.thenApply(found -> {
            if (found == null) return new Result.Reject(MessageConstants.TELEPORT_REJECTED_UNSAFE);
            return found.equals(location) ? Result.Accept.INSTANCE : new Result.Relocate(found);
        });
        result.whenComplete((value, error) -> {
            if (error != null) searching.cancel(false);
        });
        return result;
    }
}