package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.locale.tag.MessageContext;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.util.AdventureHelper;
import org.jetbrains.annotations.NotNull;

public final class BroadcastMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "broadcast");
    public static final MessageCodec<FriendlyByteBuf, BroadcastMessage> CODEC = RedisMessage.codec(BroadcastMessage::write, BroadcastMessage::new);

    private final String message;

    public BroadcastMessage(@NotNull String message) {
        this.message = message;
    }

    private BroadcastMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.message = buffer.readUtf();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUtf(this.message);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        for (BukkitSparrowPlayer receiver : SparrowPlugin.instance().playerManager().getOnlinePlayers()) {
            Component component = AdventureHelper.miniMessage().deserialize(this.message, MessageContext.of(receiver.platformPlayer));
            receiver.sendMessage(component);
        }
    }
}