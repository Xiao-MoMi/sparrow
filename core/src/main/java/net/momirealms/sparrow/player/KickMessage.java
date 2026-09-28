package net.momirealms.sparrow.player;

import io.netty.buffer.ByteBuf;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class KickMessage extends OneWayMessage<ByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "kick");
    public static final MessageCodec<ByteBuf, KickMessage> CODEC = RedisMessage.codec(KickMessage::write, KickMessage::new);

    private final UUID player;
    private final String reason;        // 空字符串表示未提供原因
    private final String operatorName;

    public KickMessage(@NotNull UUID player, @NotNull String reason, @NotNull String operatorName) {
        this.player = player;
        this.reason = reason;
        this.operatorName = operatorName;
    }

    private KickMessage(ByteBuf buffer) {
        super(buffer);
        this.player = new UUID(buffer.readLong(), buffer.readLong());
        this.reason = ByteBufHelper.readUtf8(buffer, 32767);
        this.operatorName = ByteBufHelper.readUtf8(buffer, 64);
    }

    @Override
    protected void write(ByteBuf buffer) {
        super.write(buffer);
        buffer.writeLong(this.player.getMostSignificantBits());
        buffer.writeLong(this.player.getLeastSignificantBits());
        ByteBufHelper.writeUtf8(buffer, this.reason, 32767);
        ByteBufHelper.writeUtf8(buffer, this.operatorName, 64);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    // 玩家在消息送达前已经离开时不做处理
    @Override
    protected void handle() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        SparrowPlayer target = plugin.playerManager().getPlayer(this.player);
        if (target == null) return;
        Component reason = this.reason.isEmpty() ? MessageConstants.KICK_REASON_NONE.build() : Component.text(this.reason);
        // 文本在当前线程按玩家语言渲染, 踢出放到玩家所属线程
        Component screen = plugin.translationManager().renderNested(MessageConstants.KICK_SCREEN.build().arguments(reason, Component.text(this.operatorName)), target.locale());
        plugin.scheduler().platform().run(() -> target.kick(screen), () -> {}, target.platformPlayer());
    }
}
