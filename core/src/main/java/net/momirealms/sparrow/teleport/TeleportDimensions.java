package net.momirealms.sparrow.teleport;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

public record TeleportDimensions(double width, double height, double depth) {
    private static final TeleportDimensions FALLBACK = new TeleportDimensions(0.6, 2.0, 0.6);

    @NotNull
    public static TeleportDimensions from(@NotNull ServerPlayer player) {
        if (player.getDimensions(Pose.STANDING).height() > 5.0F) return FALLBACK;
        AABB bounds = player.getBoundingBox();
        return new TeleportDimensions(bounds.getXsize(), bounds.getYsize(), bounds.getZsize());
    }
}