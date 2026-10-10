package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class Teleport {
    private final UUID player;
    private final @Nullable TeleportType type; // 为 null 时是不经过任何处理器的直接传送
    private final String server;
    private final boolean self;
    private WorldLocation location;

    public Teleport(@NotNull UUID player, @Nullable TeleportType type, @NotNull String server, @NotNull WorldLocation location, boolean self) {
        this.player = player;
        this.type = type;
        this.server = server;
        this.location = location;
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

    public boolean self() {
        return this.self;
    }

    @NotNull
    public WorldLocation location() {
        return this.location;
    }

    public void location(@NotNull WorldLocation location) {
        this.location = location;
    }
}
