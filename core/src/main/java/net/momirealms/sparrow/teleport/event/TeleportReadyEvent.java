package net.momirealms.sparrow.teleport.event;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class TeleportReadyEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID requestId;
    private final UUID playerId;
    private final String playerName;
    private final String sourceServer;
    private final String targetServer;
    private final TeleportContext context;
    private final WorldLocation location;
    private @Nullable Component reason;

    public TeleportReadyEvent(
            @NotNull UUID requestId,
            @NotNull UUID playerId,
            @NotNull String playerName,
            @NotNull String sourceServer,
            @NotNull String targetServer,
            @NotNull TeleportContext context,
            @NotNull WorldLocation location
    ) {
        super(true);
        this.requestId = requestId;
        this.playerId = playerId;
        this.playerName = playerName;
        this.sourceServer = sourceServer;
        this.targetServer = targetServer;
        this.context = context;
        this.location = location;
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
    public String sourceServer() {
        return this.sourceServer;
    }

    @NotNull
    public String targetServer() {
        return this.targetServer;
    }

    @NotNull
    public TeleportContext context() {
        return this.context;
    }

    @NotNull
    public WorldLocation location() {
        return this.location;
    }

    @Nullable
    public Component reason() {
        return this.reason;
    }

    public void cancel(@NotNull Component reason) {
        this.reason = reason;
    }

    @Override
    public boolean isCancelled() {
        return this.reason != null;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        if (!cancelled) {
            this.reason = null;
        } else if (this.reason == null) {
            this.reason = MessageConstants.TELEPORT_CANCELLED;
        }
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