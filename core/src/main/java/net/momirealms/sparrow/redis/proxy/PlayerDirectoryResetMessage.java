package net.momirealms.sparrow.redis.proxy;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PlayerDirectoryResetMessage() implements RedisMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "proxy_player_directory_reset");
    public static final MessageCodec<FriendlyByteBuf, PlayerDirectoryResetMessage> CODEC = RedisMessage.codec((message, buffer) -> {}, buffer -> new PlayerDirectoryResetMessage());
    private static volatile @Nullable Runnable listener;

    public static void listener(@Nullable Runnable listener) {
        PlayerDirectoryResetMessage.listener = listener;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    public void handle(@NotNull MessageBroker<FriendlyByteBuf> broker) {
        Runnable receiver = listener;
        if (receiver != null) {
            receiver.run();
        }
    }
}