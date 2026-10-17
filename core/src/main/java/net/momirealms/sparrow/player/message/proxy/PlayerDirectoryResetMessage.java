package net.momirealms.sparrow.player.message.proxy;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import org.jetbrains.annotations.NotNull;

public final class PlayerDirectoryResetMessage implements RedisMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "proxy_player_directory_reset");
    public static final MessageCodec<FriendlyByteBuf, PlayerDirectoryResetMessage> CODEC = RedisMessage.codec((message, buffer) -> {}, buffer -> new PlayerDirectoryResetMessage());

    private final PlayerManager playerManager = SparrowPlugin.instance().playerManager();

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    public void handle(@NotNull MessageBroker<FriendlyByteBuf> broker) {
        this.playerManager.playerCache.refreshFromRedis();
    }
}