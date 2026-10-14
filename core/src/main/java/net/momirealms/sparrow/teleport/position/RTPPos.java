package net.momirealms.sparrow.teleport.position;

import net.momirealms.sparrow.feature.rtp.RtpFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.ListTag;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessor;
import net.momirealms.sparrow.teleport.search.RandomTeleportArea;
import net.momirealms.sparrow.teleport.search.RandomTeleportSearch;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import net.momirealms.sparrow.util.SparrowKey;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public record RTPPos(
        @NotNull RandomTeleportArea area,
        int maxAttempts,
        int maxChecks,
        int horizontalRadius,
        int verticalRadius,
        boolean allowNetherRoof,
        @NotNull List<PositionProcessor> processors
) implements TeleportPosition {
    public static final SparrowKey TYPE = SparrowKey.sparrow("rtp");

    public RTPPos(@NotNull RandomTeleportArea area) {
        this(area, 32, 10_000, 2, 4, false, List.of());
    }

    public RTPPos {
        area.validate();
        if (maxAttempts <= 0 || maxChecks <= 0 || horizontalRadius < 0 || verticalRadius < 0) {
            throw new IllegalArgumentException("RTP requires positive attempt/check budgets and non-negative nearby radii");
        }
    }

    @NotNull
    public static RTPPos fromNbt(@NotNull CompoundTag tag) {
        CompoundTag area = tag.getCompound("area", null);
        if (area == null) throw new IllegalArgumentException("RTP position requires an area");
        return new RTPPos(
                RandomTeleportArea.fromNbt(area),
                tag.getInt("max_attempts", 32),
                tag.getInt("max_checks", 10_000),
                tag.getInt("horizontal_radius", 2),
                tag.getInt("vertical_radius", 4),
                tag.getBoolean("allow_nether_roof"),
                PositionProcessor.TYPES.decodeList(tag.getList("processors", new ListTag()))
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
        tag.put("area", this.area.toNbt());
        tag.putInt("max_attempts", this.maxAttempts);
        tag.putInt("max_checks", this.maxChecks);
        tag.putInt("horizontal_radius", this.horizontalRadius);
        tag.putInt("vertical_radius", this.verticalRadius);
        tag.putBoolean("allow_nether_roof", this.allowNetherRoof);
        tag.put("processors", PositionProcessor.TYPES.encodeList(this.processors));
        return tag;
    }

    @NotNull
    @Override
    public CompletableFuture<Result> resolve(@NotNull TeleportContext context) {
        if (!VersionHelper.hasPaperPatch) return CompletableFuture.completedFuture(new Result.Fail(MessageConstants.TELEPORT_REJECTED_RTP_UNSUPPORTED));
        if (!SparrowPlugin.instance().featureManager().feature(RtpFeature.ID, RtpFeature.class).enabled()) {
            return CompletableFuture.completedFuture(new Result.Fail(MessageConstants.TELEPORT_REJECTED_RTP_DISABLED));
        }
        World world = Bukkit.getWorld(this.area.world());
        if (world == null) return CompletableFuture.completedFuture(new Result.Fail(MessageConstants.TELEPORT_REJECTED_INVALID));
        RandomTeleportSearch search = new RandomTeleportSearch(((CraftWorld) world).getHandle(), context, context.getDimensions(), this);
        CompletableFuture<WorldLocation> searching = search.find();
        CompletableFuture<Result> result = searching.thenApply(location -> {
            if (location == null) return new Result.Fail(MessageConstants.TELEPORT_REJECTED_UNSAFE);
            return new Result.Success(location);
        });
        result.whenComplete((value, error) -> {
            if (error != null) searching.cancel(false);
        });
        return result;
    }
}