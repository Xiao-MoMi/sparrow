package net.momirealms.sparrow.feature.home;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

public final class HomeChangedMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "home_changed");
    public static final MessageCodec<FriendlyByteBuf, HomeChangedMessage> CODEC = RedisMessage.codec(HomeChangedMessage::write, HomeChangedMessage::new);
    private static volatile @Nullable Consumer<HomeChangedMessage> listener;

    private final String origin;
    private final UUID owner;
    private final Type type;
    private final @Nullable Home home;
    private final @Nullable HomeStore.DeleteResult deleted;

    private HomeChangedMessage(@NotNull String origin, @NotNull UUID owner, @NotNull Type type, @Nullable Home home, @Nullable HomeStore.DeleteResult deleted) {
        this.origin = origin;
        this.owner = owner;
        this.type = type;
        this.home = home;
        this.deleted = deleted;
    }

    @NotNull
    public static HomeChangedMessage save(@NotNull String origin, @NotNull Home home) {
        return new HomeChangedMessage(origin, home.owner(), Type.SAVE, home, null);
    }

    @NotNull
    public static HomeChangedMessage delete(@NotNull String origin, @NotNull HomeStore.DeleteResult deleted) {
        return new HomeChangedMessage(origin, deleted.owner(), Type.DELETE, null, deleted);
    }

    @NotNull
    public static HomeChangedMessage invalidateOwner(@NotNull String origin, @NotNull UUID owner) {
        return new HomeChangedMessage(origin, owner, Type.INVALIDATE_OWNER, null, null);
    }

    private HomeChangedMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.origin = ByteBufHelper.readUtf8(buffer, 255);
        this.owner = buffer.readUUID();
        this.type = Type.values()[buffer.readByte()];
        this.home = this.type == Type.SAVE ? Home.read(buffer) : null;
        // 小写转换可能扩展字符数, 名称键不能按显示名称的长度截断.
        this.deleted = this.type == Type.DELETE ? new HomeStore.DeleteResult(this.owner, buffer.readUUID(), ByteBufHelper.readUtf8(buffer, Home.MAX_NAME_LENGTH * 3)) : null;
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.origin, 255);
        buffer.writeUUID(this.owner);
        buffer.writeByte(this.type.ordinal());
        if (this.home != null) {
            this.home.write(buffer);
        }
        if (this.deleted != null) {
            buffer.writeUUID(this.deleted.id());
            ByteBufHelper.writeUtf8(buffer, this.deleted.nameKey(), Home.MAX_NAME_LENGTH * 3);
        }
    }

    @NotNull
    public String origin() {
        return this.origin;
    }

    @NotNull
    public UUID owner() {
        return this.owner;
    }

    @NotNull
    public Type type() {
        return this.type;
    }

    @Nullable
    public Home home() {
        return this.home;
    }

    @Nullable
    public HomeStore.DeleteResult deleted() {
        return this.deleted;
    }

    static void listener(@Nullable Consumer<HomeChangedMessage> listener) {
        HomeChangedMessage.listener = listener;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        Consumer<HomeChangedMessage> listener = HomeChangedMessage.listener;
        if (listener != null) {
            listener.accept(this);
        }
    }

    public enum Type {
        SAVE, DELETE, INVALIDATE_OWNER
    }
}