package net.momirealms.sparrow.player.message;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class PlayerChangedMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "player_changed");
    public static final MessageCodec<FriendlyByteBuf, PlayerChangedMessage> CODEC = RedisMessage.codec(PlayerChangedMessage::write, PlayerChangedMessage::new);

    private final UUID uniqueId;
    private final @Nullable String serverId; // null 表示删除记录, 此时名字和语言也为 null
    private final @Nullable String name;
    private final @Nullable Locale locale;

    public PlayerChangedMessage(@NotNull UUID uniqueId, @NotNull String serverId, @NotNull String name, @NotNull Locale locale) {
        this.uniqueId = uniqueId;
        this.serverId = serverId;
        this.name = name;
        this.locale = locale;
    }

    public PlayerChangedMessage(@NotNull UUID uniqueId) {
        this.uniqueId = uniqueId;
        this.serverId = null;
        this.name = null;
        this.locale = null;
    }

    private PlayerChangedMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.uniqueId = buffer.readUUID();
        if (buffer.readBoolean()) {
            this.serverId = readServerId(buffer);
            this.name = ByteBufHelper.readUtf8(buffer, 64);
            this.locale = Locale.forLanguageTag(ByteBufHelper.readUtf8(buffer, 128));
        } else {
            this.serverId = null;
            this.name = null;
            this.locale = null;
        }
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.uniqueId);
        buffer.writeBoolean(this.serverId != null);
        if (this.serverId != null) {
            writeRecord(buffer, this.serverId, this.name, this.locale);
        }
    }

    // Redis 名单值和消息共用这段记录编码
    public static void writeRecord(@NotNull ByteBuf buffer, @NotNull String serverId, @NotNull String name, @NotNull Locale locale) {
        buffer.writeBytes(serverPrefix(serverId));
        ByteBufHelper.writeUtf8(buffer, name, 64);
        ByteBufHelper.writeUtf8(buffer, locale.toLanguageTag(), 128);
    }

    // UUID 由 Redis Hash 字段提供
    @NotNull
    public static PlayerChangedMessage readRecord(@NotNull UUID uniqueId, @NotNull ByteBuf buffer) {
        String serverId = readServerId(buffer);
        String name = ByteBufHelper.readUtf8(buffer, 64);
        return new PlayerChangedMessage(uniqueId, serverId, name, Locale.forLanguageTag(ByteBufHelper.readUtf8(buffer, 128)));
    }

    // 1 字节长度加 UTF-8 服务器 ID, 名单脚本按这段前缀判断记录归属
    public static byte @NotNull [] serverPrefix(@NotNull String serverId) {
        byte[] id = serverId.getBytes(StandardCharsets.UTF_8);
        byte[] prefix = new byte[id.length + 1];
        prefix[0] = (byte) id.length;
        System.arraycopy(id, 0, prefix, 1, id.length);
        return prefix;
    }

    private static String readServerId(ByteBuf buffer) {
        return buffer.readCharSequence(buffer.readUnsignedByte(), StandardCharsets.UTF_8).toString();
    }

    @NotNull
    public UUID uniqueId() {
        return this.uniqueId;
    }

    @Nullable
    public String serverId() {
        return this.serverId;
    }

    @Nullable
    public String name() {
        return this.name;
    }

    @Nullable
    public Locale locale() {
        return this.locale;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    // 本服发布的变化同样在回显时写入名单
    @Override
    protected void handle() {
        SparrowPlugin.instance().playerManager().registry.receive(this);
    }
}