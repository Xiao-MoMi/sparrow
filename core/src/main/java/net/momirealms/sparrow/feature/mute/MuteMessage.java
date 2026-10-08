package net.momirealms.sparrow.feature.mute;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class MuteMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "mute");
    public static final MessageCodec<FriendlyByteBuf, MuteMessage> CODEC = RedisMessage.codec(MuteMessage::write, MuteMessage::new);
    private static volatile @Nullable Consumer<MuteMessage> listener;

    private final String origin;
    private final MuteRecord record;

    public MuteMessage(@NotNull String origin, @NotNull MuteRecord record) {
        this.origin = origin;
        this.record = record;
    }

    private MuteMessage(@NotNull FriendlyByteBuf buffer) {
        super(buffer);
        this.origin = ByteBufHelper.readUtf8(buffer, 255);
        this.record = new MuteRecord(
                ByteBufHelper.readUtf8(buffer, 36),
                buffer.readUUID(),
                ByteBufHelper.readUtf8(buffer, 64),
                ByteBufHelper.readUtf8(buffer, 32767),
                ByteBufHelper.readUtf8(buffer, 64),
                ByteBufHelper.readUtf8(buffer, 255),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readBoolean() ? ByteBufHelper.readUtf8(buffer, 64) : null
        );
    }

    @Override
    protected void write(@NotNull FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.origin, 255);
        ByteBufHelper.writeUtf8(buffer, this.record.id(), 36);
        buffer.writeUUID(this.record.player());
        ByteBufHelper.writeUtf8(buffer, this.record.playerName(), 64);
        ByteBufHelper.writeUtf8(buffer, this.record.reason(), 32767);
        ByteBufHelper.writeUtf8(buffer, this.record.operatorName(), 64);
        ByteBufHelper.writeUtf8(buffer, this.record.server(), 255);
        buffer.writeLong(this.record.createdAt());
        buffer.writeLong(this.record.expiresAt());
        buffer.writeLong(this.record.revokedAt());
        buffer.writeBoolean(this.record.revokedBy() != null);
        if (this.record.revokedBy() != null) {
            ByteBufHelper.writeUtf8(buffer, this.record.revokedBy(), 64);
        }
    }

    static void listener(@Nullable Consumer<MuteMessage> listener) {
        MuteMessage.listener = listener;
    }

    @NotNull
    public String origin() {
        return this.origin;
    }

    @NotNull
    public MuteRecord record() {
        return this.record;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        Consumer<MuteMessage> listener = MuteMessage.listener;
        if (listener != null) {
            listener.accept(this);
        }
    }
}