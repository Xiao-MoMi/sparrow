package net.momirealms.sparrow.feature.spawn;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class SpawnMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "spawn");
    public static final MessageCodec<FriendlyByteBuf, SpawnMessage> CODEC = RedisMessage.codec(SpawnMessage::write, SpawnMessage::new);
    private static volatile @Nullable Consumer<SpawnMessage> listener;

    private final String origin;
    private final @Nullable Spawn spawn;

    public SpawnMessage(@NotNull String origin, @Nullable Spawn spawn) {
        this.origin = origin;
        this.spawn = spawn;
    }

    private SpawnMessage(@NotNull FriendlyByteBuf buffer) {
        super(buffer);
        this.origin = ByteBufHelper.readUtf8(buffer, 255);
        this.spawn = buffer.readBoolean() ? new Spawn(
                ByteBufHelper.readUtf8(buffer, 255),
                new WorldLocation(
                        ByteBufHelper.readUtf8(buffer, 255),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readFloat(),
                        buffer.readFloat()
                )
        ) : null;
    }

    @Override
    protected void write(@NotNull FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.origin, 255);
        buffer.writeBoolean(this.spawn != null);
        if (this.spawn != null) {
            ByteBufHelper.writeUtf8(buffer, this.spawn.server(), 255);
            WorldLocation location = this.spawn.location();
            ByteBufHelper.writeUtf8(buffer, location.world(), 255);
            buffer.writeDouble(location.x());
            buffer.writeDouble(location.y());
            buffer.writeDouble(location.z());
            buffer.writeFloat(location.yaw());
            buffer.writeFloat(location.pitch());
        }
    }

    static void listener(@Nullable Consumer<SpawnMessage> listener) {
        SpawnMessage.listener = listener;
    }

    @NotNull
    public String origin() {
        return this.origin;
    }

    @Nullable
    public Spawn spawn() {
        return this.spawn;
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        Consumer<SpawnMessage> listener = SpawnMessage.listener;
        if (listener != null) {
            listener.accept(this);
        }
    }
}
