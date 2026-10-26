package net.momirealms.sparrow.teleport.event;

import net.momirealms.sparrow.util.ServerLocation;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class TeleportArriveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID requestId;
    private final UUID playerId;
    private final String playerName;
    private final ServerLocation from;
    private final ServerLocation to;
    private final long sourceConnectedAt;
    private final long departedAt;

    public TeleportArriveEvent(
            @NotNull UUID requestId,
            @NotNull UUID playerId,
            @NotNull String playerName,
            @NotNull ServerLocation from,
            @NotNull ServerLocation to,
            long sourceConnectedAt,
            long departedAt
    ) {
        super(!Bukkit.isPrimaryThread());
        this.requestId = requestId;
        this.playerId = playerId;
        this.playerName = playerName;
        this.from = from;
        this.to = to;
        this.sourceConnectedAt = sourceConnectedAt;
        this.departedAt = departedAt;
    }

    @NotNull
    public UUID requestId() {
        return this.requestId;
    }

    @NotNull
    public UUID playerId() {
        return this.playerId;
    }

    @NotNull
    public String playerName() {
        return this.playerName;
    }

    @NotNull
    public ServerLocation from() {
        return this.from;
    }

    @NotNull
    public ServerLocation to() {
        return this.to;
    }

    public long sourceConnectedAt() {
        return this.sourceConnectedAt;
    }

    public long departedAt() {
        return this.departedAt;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}