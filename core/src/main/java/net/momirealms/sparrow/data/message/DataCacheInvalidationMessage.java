package net.momirealms.sparrow.data.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record DataCacheInvalidationMessage(
        @NotNull SparrowKey id,
        @NotNull String storagePrefix,
        @NotNull UUID origin,
        @Nullable List<String> keys
) implements RedisMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "data_cache_invalidation");
    public static final MessageCodec<FriendlyByteBuf, DataCacheInvalidationMessage> CODEC = RedisMessage.codec(DataCacheInvalidationMessage::write, DataCacheInvalidationMessage::read);

    // 用独立标志区分整域失效与指定键列表, 空列表按原样传输.
    private void write(FriendlyByteBuf buffer) {
        ByteBufHelper.writeUtf8(buffer, this.id.toString(), 32767);
        ByteBufHelper.writeUtf8(buffer, this.storagePrefix, 32767);
        buffer.writeUUID(this.origin);
        buffer.writeBoolean(this.keys != null);
        if (this.keys != null) {
            buffer.writeVarInt(this.keys.size());
            for (String key : this.keys) {
                ByteBufHelper.writeUtf8(buffer, key, 32767);
            }
        }
    }

    // 恢复失效范围, 已解码的键列表保持只读.
    private static DataCacheInvalidationMessage read(FriendlyByteBuf buffer) {
        SparrowKey id = SparrowKey.of(ByteBufHelper.readUtf8(buffer, 32767));
        String storagePrefix = ByteBufHelper.readUtf8(buffer, 32767);
        UUID origin = buffer.readUUID();
        List<String> keys = null;
        if (buffer.readBoolean()) {
            int size = buffer.readVarInt();
            keys = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                keys.add(ByteBufHelper.readUtf8(buffer, 32767));
            }
            keys = List.copyOf(keys);
        }
        return new DataCacheInvalidationMessage(id, storagePrefix, origin, keys);
    }

    /** 将失效范围交给本服数据入口, 在消息边界报告异步刷新失败. */
    @Override
    public void handle(@NotNull MessageBroker<FriendlyByteBuf> broker) {
        SparrowPlugin plugin = SparrowPlugin.instance();
        plugin.dataAccess().acceptInvalidation(this).whenComplete((ignored, error) -> {
            if (error != null) {
                plugin.logger().warn("Failed to refresh data cache " + this.id, error);
            }
        });
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }
}