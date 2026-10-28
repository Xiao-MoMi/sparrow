package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.proxy.bukkit.util.CraftChatMessageProxy;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.util.AdventureHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class SendMessageMessage extends AbstractPlayerTextMessage {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "send_player_message");
    public static final MessageCodec<FriendlyByteBuf, SendMessageMessage> CODEC = RedisMessage.codec(SendMessageMessage::write, SendMessageMessage::new);

    private final Text message;
    private final boolean overlay;

    public SendMessageMessage(@NotNull UUID playerId, @NotNull Component message) {
        this(playerId, message, false);
    }

    public SendMessageMessage(@NotNull UUID playerId, @NotNull Component message, boolean overlay) {
        super(playerId);
        this.message = new Text(message);
        this.overlay = overlay;
    }

    public SendMessageMessage(@NotNull UUID playerId, @NotNull String message) {
        this(playerId, message, false);
    }

    public SendMessageMessage(@NotNull UUID playerId, @NotNull String message, boolean overlay) {
        super(playerId);
        this.message = new Text(message);
        this.overlay = overlay;
    }

    private SendMessageMessage(@NotNull UUID playerId, @NotNull Text message, boolean overlay) {
        super(playerId);
        this.message = message;
        this.overlay = overlay;
    }

    @NotNull
    public static SendMessageMessage translated(@NotNull UUID playerId, @NotNull Component message) {
        return translated(playerId, message, false);
    }

    @NotNull
    public static SendMessageMessage translated(@NotNull UUID playerId, @NotNull Component message, boolean overlay) {
        return new SendMessageMessage(playerId, Text.translated(message), overlay);
    }

    private SendMessageMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.message = new Text(buffer);
        this.overlay = buffer.readBoolean();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        this.message.write(buffer);
        buffer.writeBoolean(this.overlay);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void send(@NotNull BukkitSparrowPlayer player) {
        Object component = CraftChatMessageProxy.INSTANCE.fromJSON(AdventureHelper.componentToJson(this.message.resolve(player)));
        ServerPlayerProxy.INSTANCE.sendSystemMessage(player.nmsPlayer, component, this.overlay);
    }
}