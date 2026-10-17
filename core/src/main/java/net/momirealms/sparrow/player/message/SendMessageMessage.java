package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.proxy.bukkit.util.CraftChatMessageProxy;
import net.momirealms.sparrow.proxy.minecraft.server.level.ServerPlayerProxy;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class SendMessageMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "send_player_message");
    public static final MessageCodec<FriendlyByteBuf, SendMessageMessage> CODEC = RedisMessage.codec(SendMessageMessage::write, SendMessageMessage::new);

    private final UUID playerId;
    private final Component message;
    private final boolean overlay;

    public SendMessageMessage(@NotNull UUID playerId, @NotNull Component message) {
        this(playerId, message, false);
    }

    public SendMessageMessage(@NotNull UUID playerId, @NotNull Component message, boolean overlay) {
        this.playerId = playerId;
        this.message = message;
        this.overlay = overlay;
    }

    private SendMessageMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.message = AdventureHelper.jsonToComponent(ByteBufHelper.readUtf8(buffer, 262144));
        this.overlay = buffer.readBoolean();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        ByteBufHelper.writeUtf8(buffer, AdventureHelper.componentToJson(this.message), 262144);
        buffer.writeBoolean(this.overlay);
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        BukkitSparrowPlayer player = SparrowPlugin.instance().playerManager().getOnlinePlayer(this.playerId);
        if (player == null) return;
        Object component = CraftChatMessageProxy.INSTANCE.fromJSON(AdventureHelper.componentToJson(this.message));
        ServerPlayerProxy.INSTANCE.sendSystemMessage(player.nmsPlayer, component, this.overlay);
    }
}