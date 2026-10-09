package net.momirealms.sparrow.redis.proxy;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.cluster.PlayerDirectory;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import org.jetbrains.annotations.NotNull;

public final class PlayerDirectoryResetMessage implements RedisMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "proxy_player_directory_reset");
    public static final MessageCodec<FriendlyByteBuf, PlayerDirectoryResetMessage> CODEC = RedisMessage.codec((message, buffer) -> {}, buffer -> new PlayerDirectoryResetMessage());

    private final PlayerDirectory directory = SparrowPlugin.instance().playerDirectory();

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    public void handle(@NotNull MessageBroker<FriendlyByteBuf> broker) {
        this.directory.reload();
    }
}