package net.momirealms.sparrow.feature.ban;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.IpRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 封禁或解封通知. 封禁时收到的服务器踢出本服命中的玩家, 非静默时通知本服有权限的玩家.
 */
public final class BanMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "ban");
    public static final MessageCodec<FriendlyByteBuf, BanMessage> CODEC = RedisMessage.codec(BanMessage::write, BanMessage::new);
    private static final int HAS_PLAYER = 1;
    private static final int HAS_IP = 2;

    private final BanFeature feature = SparrowPlugin.instance().featureManager().feature(BanFeature.ID, BanFeature.class);
    private final boolean banned;           // true 为封禁, false 为解封
    private final String banId;             // 封禁时为新记录的 ID, 解封时为空字符串
    private final String display;           // 通知里展示的对象
    private final @Nullable UUID player;    // 封禁时用于踢出本服玩家
    private final @Nullable IpRange ip;
    private final String reason;            // 空字符串表示未提供原因
    private final String operatorName;
    private final long createdAt;
    private final long expiresAt;           // 0 表示永久, 解封时为 0
    private final boolean silent;           // 只踢人, 不通知管理员

    public BanMessage(boolean banned,
                      @NotNull String banId,
                      @NotNull String display,
                      @Nullable UUID player,
                      @Nullable IpRange ip,
                      @NotNull String reason,
                      @NotNull String operatorName,
                      long createdAt,
                      long expiresAt,
                      boolean silent) {
        this.banned = banned;
        this.banId = banId;
        this.display = display;
        this.player = player;
        this.ip = ip;
        this.reason = reason;
        this.operatorName = operatorName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.silent = silent;
    }

    private BanMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.banned = buffer.readBoolean();
        this.banId = ByteBufHelper.readUtf8(buffer, BanRecord.ID_LENGTH);
        this.display = ByteBufHelper.readUtf8(buffer, 255);
        int flags = buffer.readByte();
        this.player = (flags & HAS_PLAYER) != 0 ? buffer.readUUID() : null;
        this.ip = (flags & HAS_IP) != 0 ? new IpRange(buffer.readLong(), buffer.readLong()) : null;
        this.reason = ByteBufHelper.readUtf8(buffer, 32767);
        this.operatorName = ByteBufHelper.readUtf8(buffer, 64);
        this.createdAt = buffer.readLong();
        this.expiresAt = buffer.readLong();
        this.silent = buffer.readBoolean();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeBoolean(this.banned);
        ByteBufHelper.writeUtf8(buffer, this.banId, BanRecord.ID_LENGTH);
        ByteBufHelper.writeUtf8(buffer, this.display, 255);
        buffer.writeByte((this.player != null ? HAS_PLAYER : 0) | (this.ip != null ? HAS_IP : 0));
        if (this.player != null) {
            buffer.writeUUID(this.player);
        }
        if (this.ip != null) {
            buffer.writeLong(this.ip.start()).writeLong(this.ip.end());
        }
        ByteBufHelper.writeUtf8(buffer, this.reason, 32767);
        ByteBufHelper.writeUtf8(buffer, this.operatorName, 64);
        buffer.writeLong(this.createdAt);
        buffer.writeLong(this.expiresAt);
        buffer.writeBoolean(this.silent);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    // 本服未启用封禁模块时忽略
    @Override
    protected void handle() {
        this.feature.accept(this);
    }

    public boolean banned() {
        return this.banned;
    }

    @NotNull
    public String banId() {
        return this.banId;
    }

    @NotNull
    public String display() {
        return this.display;
    }

    @Nullable
    public UUID player() {
        return this.player;
    }

    @Nullable
    public IpRange ip() {
        return this.ip;
    }

    @NotNull
    public String reason() {
        return this.reason;
    }

    @NotNull
    public String operatorName() {
        return this.operatorName;
    }

    public long createdAt() {
        return this.createdAt;
    }

    public long expiresAt() {
        return this.expiresAt;
    }

    public boolean silent() {
        return this.silent;
    }
}