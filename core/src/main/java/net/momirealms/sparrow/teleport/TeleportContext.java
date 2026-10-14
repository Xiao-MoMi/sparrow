package net.momirealms.sparrow.teleport;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.Tag;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TeleportContext {
    public static final String PLAYER_UUID = "player_uuid";
    public static final String PLAYER_NAME = "player_name";
    public static final String UNSAFE = "unsafe";
    public static final String DIMENSIONS = "dimensions";

    private final CompoundTag tag;

    public TeleportContext() {
        this(new CompoundTag());
    }

    public TeleportContext(@NotNull CompoundTag tag) {
        this.tag = tag;
    }

    @NotNull
    public static TeleportContext fromNbt(@NotNull CompoundTag tag) {
        return new TeleportContext(tag);
    }

    @NotNull
    public TeleportContext put(@NotNull String key, @NotNull Tag value) {
        this.tag.put(key, value);
        return this;
    }

    @Nullable
    public Tag get(@NotNull String key) {
        return this.tag.get(key);
    }

    public void remove(@NotNull String key) {
        this.tag.remove(key);
    }

    @NotNull
    public CompoundTag toNbt() {
        return this.tag;
    }

    @NotNull
    public Dimensions getDimensions() {
        if (!(this.tag.get(DIMENSIONS) instanceof CompoundTag dimensions)) {
            throw new IllegalStateException("Teleport context does not contain player dimensions");
        }
        double width = dimensions.getDouble("width", Double.NaN);
        double height = dimensions.getDouble("height", Double.NaN);
        double depth = dimensions.getDouble("depth", Double.NaN);
        double eyeHeight = dimensions.getDouble("eye_height", Double.NaN);
        if (!Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(depth) || !Double.isFinite(eyeHeight)
                || width <= 0 || height <= 0 || depth <= 0 || eyeHeight < 0) {
            throw new IllegalArgumentException("Teleport dimensions must be finite and positive, with a non-negative eye height");
        }
        return new Dimensions(width, height, depth, eyeHeight);
    }

    void bindPlayer(@NotNull BukkitSparrowPlayer player) {
        this.tag.putUUID(PLAYER_UUID, player.uniqueId());
        this.tag.putString(PLAYER_NAME, player.name());
        ServerPlayer nmsPlayer = player.nmsPlayer();
        double width;
        double height;
        double depth;
        double eyeHeight;
        if (nmsPlayer.getDimensions(Pose.STANDING).height() > 5.0F) {
            width = 0.6;
            height = 2.0;
            depth = 0.6;
            eyeHeight = 1.62F;
        } else {
            AABB bounds = nmsPlayer.getBoundingBox();
            width = bounds.getXsize();
            height = bounds.getYsize();
            depth = bounds.getZsize();
            eyeHeight = nmsPlayer.getEyeHeight();
        }
        CompoundTag dimensions = new CompoundTag();
        dimensions.putDouble("width", width);
        dimensions.putDouble("height", height);
        dimensions.putDouble("depth", depth);
        dimensions.putDouble("eye_height", eyeHeight);
        this.tag.put(DIMENSIONS, dimensions);
    }

    @ApiStatus.Internal
    public record Dimensions(double width, double height, double depth, double eyeHeight) {
    }
}