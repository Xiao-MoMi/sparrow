package net.momirealms.sparrow.data.message;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// values 中的 null 表示对应条目被删除
@ApiStatus.Internal
public record DataChangedMessage(
        @NotNull SparrowKey id,
        byte @NotNull [] key,
        byte @NotNull [] @NotNull [] entries,
        byte @NotNull [] @Nullable [] values,
        long version
) implements RedisMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "data_changed");
    public static final MessageCodec<FriendlyByteBuf, DataChangedMessage> CODEC = RedisMessage.codec(DataChangedMessage::write, DataChangedMessage::read);
    private static final int MAX_TEXT_LENGTH = 32767;
    private static final int MAX_VERSION_LENGTH = 19;

    private void write(ByteBuf buffer) {
        writePrefix(buffer, this.id, this.key, this.entries, this.values);
        ByteBufHelper.writeUtf8(buffer, Long.toString(this.version), MAX_VERSION_LENGTH);
    }

    // 版本由提交脚本在末尾追加, 长度只占一个字节
    public static void writePrefix(@NotNull ByteBuf buffer, @NotNull SparrowKey id, byte @NotNull [] key, byte @NotNull [] @NotNull [] entries, byte @NotNull [] @Nullable [] values) {
        ByteBufHelper.writeUtf8(buffer, id.toString(), MAX_TEXT_LENGTH);
        ByteBufHelper.writeByteArray(buffer, key);
        ByteBufHelper.writeCompactInt(buffer, entries.length);
        for (int i = 0; i < entries.length; i++) {
            ByteBufHelper.writeByteArray(buffer, entries[i]);
            buffer.writeBoolean(values[i] != null);
            if (values[i] != null) {
                ByteBufHelper.writeByteArray(buffer, values[i]);
            }
        }
    }

    private static DataChangedMessage read(ByteBuf buffer) {
        SparrowKey id = SparrowKey.of(ByteBufHelper.readUtf8(buffer, MAX_TEXT_LENGTH));
        byte[] key = ByteBufHelper.readByteArray(buffer, buffer.readableBytes());
        int size = ByteBufHelper.readCompactInt(buffer);
        byte[][] entries = new byte[size][];
        byte[][] values = new byte[size][];
        for (int i = 0; i < size; i++) {
            entries[i] = ByteBufHelper.readByteArray(buffer, buffer.readableBytes());
            if (buffer.readBoolean()) {
                values[i] = ByteBufHelper.readByteArray(buffer, buffer.readableBytes());
            }
        }
        long version = Long.parseLong(ByteBufHelper.readUtf8(buffer, MAX_VERSION_LENGTH));
        return new DataChangedMessage(id, key, entries, values, version);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    public void handle(MessageBroker<FriendlyByteBuf> broker) {
        SparrowPlugin.instance().dataAccess().receive(this);
    }
}