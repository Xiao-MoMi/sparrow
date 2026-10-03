package net.momirealms.sparrow.player;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class BroadcastMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "broadcast");
    public static final MessageCodec<FriendlyByteBuf, BroadcastMessage> CODEC = RedisMessage.codec(BroadcastMessage::write, BroadcastMessage::new);

    private final String message;
    private final boolean legacy;
    private final boolean placeholders;

    public BroadcastMessage(@NotNull String message, boolean legacy, boolean placeholders) {
        this.message = message;
        this.legacy = legacy;
        this.placeholders = placeholders;
    }

    private BroadcastMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.message = buffer.readUtf();
        this.legacy = buffer.readBoolean();
        this.placeholders = buffer.readBoolean();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUtf(this.message);
        buffer.writeBoolean(this.legacy);
        buffer.writeBoolean(this.placeholders);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        for (SparrowPlayer receiver : plugin.playerManager().getOnlinePlayers()) {
            Player player = receiver.platformPlayer();
            // 占位符读取玩家状态, 与消息发送一起在玩家所属线程执行.
            plugin.scheduler().platform().run(() -> {
                String text = this.placeholders ? plugin.compatibilityManager().parsePlaceholders(player, this.message) : this.message;
                Component component = AdventureHelper.miniMessage(text, this.legacy);
                receiver.sendMessage(component);
            }, () -> {}, player);
        }
    }
}
