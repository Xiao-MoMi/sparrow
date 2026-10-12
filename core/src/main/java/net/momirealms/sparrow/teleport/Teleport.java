package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class Teleport {
    private final UUID player;
    private final @Nullable TeleportType type; // 为 null 时是不经过任何处理器的直接传送
    private final String server;
    private final TeleportDestination destination;
    private final boolean self;
    private TeleportDimensions dimensions; // 有类型的传送在出发前处理完成后采集, 直接传送为 null
    private WorldLocation location; // 目标解析成功后保存的最终位置, 在此之前为 null

    public Teleport(@NotNull UUID player, @Nullable TeleportType type, @NotNull String server, @NotNull TeleportDestination destination, boolean self) {
        this.player = player;
        this.type = type;
        this.server = server;
        this.destination = destination;
        this.self = self;
    }

    @NotNull
    public UUID player() {
        return this.player;
    }

    @Nullable
    public TeleportType type() {
        return this.type;
    }

    @NotNull
    public String server() {
        return this.server;
    }

    @NotNull
    public TeleportDestination destination() {
        return this.destination;
    }

    public boolean self() {
        return this.self;
    }

    // 有类型的传送在落点阶段及之后可用
    @NotNull
    public TeleportDimensions dimensions() {
        return this.dimensions;
    }

    public void dimensions(@NotNull TeleportDimensions dimensions) {
        this.dimensions = dimensions;
    }

    // 只保存通过规则的最终位置, 后续传送和到达处理使用同一坐标
    @NotNull
    CompletableFuture<TeleportDestination.Result> locate(DestinationContext context) {
        CompletableFuture<TeleportDestination.Result> resolved = this.destination.resolve(context);
        CompletableFuture<TeleportDestination.Result> located = resolved.thenApply(result -> {
            if (result.location() != null) this.location = result.location();
            return result;
        });
        located.whenComplete((result, error) -> {
            if (located.isCancelled()) resolved.cancel(false);
        });
        return located;
    }

    // 只在落点阶段及之后可用
    @NotNull
    public WorldLocation location() {
        return this.location;
    }
}