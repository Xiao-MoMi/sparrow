package net.momirealms.sparrow.redis.message.player;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
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
    private final boolean legacy;

    public BroadcastMessage(@NotNull String message, boolean legacy) {
        this.message = message;
        this.legacy = legacy;
    }

    private BroadcastMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.message = buffer.readUtf();
        this.legacy = buffer.readBoolean();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUtf(this.message);
        buffer.writeBoolean(this.legacy);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        for (BukkitSparrowPlayer receiver : plugin.playerManager().getOnlinePlayers()) {
            Component component = AdventureHelper.miniMessage(this.message, this.legacy, receiver.platformPlayer());
            receiver.sendMessage(component);
        }
    }
}