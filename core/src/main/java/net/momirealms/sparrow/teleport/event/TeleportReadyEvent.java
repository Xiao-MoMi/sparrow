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

    private final UUID requestId;           // 传送请求 ID
    private final UUID playerId;            // 被传送玩家的 UUID
    private final String playerName;        // 被传送玩家的名字
    private final String sourceServer;      // 玩家当前所在的源服 ID
    private final String targetServer;      // 本服 ID, 也就是目标服
    private final TeleportContext context;  // 传送上下文, 监听器可以读写
    private final WorldLocation location;   // 最终的目的地坐标
    private @Nullable Component reason;     // 取消原因, 为 null 表示没有取消

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