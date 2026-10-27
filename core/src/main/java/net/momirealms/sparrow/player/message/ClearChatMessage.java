package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class ClearChatMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "clear_chat");
    public static final MessageCodec<FriendlyByteBuf, ClearChatMessage> CODEC = RedisMessage.codec(ClearChatMessage::write, ClearChatMessage::new);
    private static final int CLEAR_LINES = 100;
    private static final Component BLANK_LINE = Component.text(" ");

    private final UUID playerId;

    public ClearChatMessage(@NotNull UUID playerId) {
        this.playerId = playerId;
    }

    private ClearChatMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        this.execute();
    }

    public void execute() {
        BukkitSparrowPlayer player = SparrowPlugin.instance().playerManager().getOnlinePlayer(this.playerId);
        if (player == null) return;
        for (int i = 0; i < CLEAR_LINES; i++) {
            player.sendMessage(BLANK_LINE);
        }
    }
}