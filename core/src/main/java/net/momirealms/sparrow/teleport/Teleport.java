package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class Teleport {
    private final UUID player;
    private final @Nullable TeleportType type; // 为 null 时是不经过任何处理器的直接传送
    private final String server;
    private final TeleportDestination destination;
    private final boolean self;
    private WorldLocation location; // 落点阶段开始时由落点所在的服务器算出, 在此之前为 null

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

    // 落点阶段开始时由落点所在的服务器调用, 之后落点处理器检查和改写的都是算出的这个位置. 算不出落点时返回 false
    boolean locate() {
        WorldLocation location = this.destination.resolve();
        if (location == null) return false;
        this.location = location;
        return true;
    }

    // 只在落点阶段及之后可用
    @NotNull
    public WorldLocation location() {
        return this.location;
    }

    public void location(@NotNull WorldLocation location) {
        this.location = location;
    }
}
