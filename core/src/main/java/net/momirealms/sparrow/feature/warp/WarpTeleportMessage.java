package net.momirealms.sparrow.feature.warp;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.util.ThrowableUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class WarpTeleportMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "warp_teleport");
    public static final MessageCodec<FriendlyByteBuf, WarpTeleportMessage> CODEC = RedisMessage.codec(WarpTeleportMessage::write, WarpTeleportMessage::new);

    private final UUID playerId;
    private final String playerName;
    private final UUID warpId;
    private final String warpName;
    private final @Nullable UUID requester;
    private final String origin;

    public WarpTeleportMessage(@NotNull UUID playerId, @NotNull String playerName, @NotNull UUID warpId, @NotNull String warpName, @Nullable UUID requester) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.warpId = warpId;
        this.warpName = warpName;
        this.requester = requester;
        this.origin = ServerConfig.serverId();
    }

    private WarpTeleportMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.playerName = buffer.readUtf();
        this.warpId = buffer.readUUID();
        this.warpName = buffer.readUtf();
        this.requester = buffer.readBoolean() ? buffer.readUUID() : null;
        this.origin = buffer.readUtf();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        buffer.writeUtf(this.playerName);
        buffer.writeUUID(this.warpId);
        buffer.writeUtf(this.warpName);
        buffer.writeBoolean(this.requester != null);
        if (this.requester != null) buffer.writeUUID(this.requester);
        buffer.writeUtf(this.origin);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        this.execute();
    }

    public void execute() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer player = plugin.playerManager().getOnlinePlayer(this.playerId);
        if (player == null) {
            this.respond(MessageConstants.COMMAND_PLAYER_OFFLINE.arguments(Component.text(this.playerName)));
            return;
        }
        CompletableFuture<CompletableFuture<@Nullable Component>> scheduled = new CompletableFuture<>();
        scheduled.completeAsync(() -> {
            if (!player.isOnlineLocally()) return CompletableFuture.completedFuture(MessageConstants.TELEPORT_UNAVAILABLE);
            if (!plugin.featureManager().isEnabled(WarpFeature.ID)) return CompletableFuture.completedFuture(MessageConstants.TELEPORT_DISABLED);
            WarpFeature feature = plugin.featureManager().feature(WarpFeature.ID, WarpFeature.class);
            Warp warp = feature.registry().get(this.warpId);
            return warp == null
                    ? CompletableFuture.completedFuture(MessageConstants.COMMAND_WARP_UNKNOWN.arguments(Component.text(this.warpName)))
                    : feature.teleport(player, warp);
        }, task -> plugin.scheduler().platform().run(
                task,
                () -> scheduled.complete(CompletableFuture.completedFuture(MessageConstants.TELEPORT_UNAVAILABLE)),
                player.platformPlayer
        ));
        scheduled.thenCompose(result -> result).whenComplete((reason, error) -> {
            if (error != null) {
                plugin.logger().warn("Warp teleport failed for " + this.playerName, ThrowableUtils.unwrap(error));
                this.respond(MessageConstants.TELEPORT_FAILED);
            } else {
                this.respond(reason);
            }
        });
    }

    private void respond(@Nullable Component reason) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        Component warp = Component.text(this.warpName);
        OfflineSparrowPlayer traveler = plugin.playerManager().getGlobalOnlinePlayer(this.playerId);
        if (traveler != null) {
            traveler.sendTranslatedMessage(reason == null ? MessageConstants.COMMAND_WARP_SUCCESS_SELF.arguments(warp) : reason);
        }
        if (this.playerId.equals(this.requester)) return;
        Component message = reason == null
                ? MessageConstants.COMMAND_WARP_SUCCESS.arguments(Component.text(this.playerName), warp)
                : MessageConstants.COMMAND_WARP_FAILED.arguments(Component.text(this.playerName), warp, reason);
        if (this.requester != null) {
            OfflineSparrowPlayer sender = plugin.playerManager().getGlobalOnlinePlayer(this.requester);
            if (sender != null) sender.sendTranslatedMessage(message);
        } else {
            WarpFeedbackMessage feedback = new WarpFeedbackMessage(message);
            if (this.origin.equals(ServerConfig.serverId())) {
                feedback.handle();
            } else {
                plugin.messageBrokerManager().publishOneWay(feedback, this.origin);
            }
        }
    }
}