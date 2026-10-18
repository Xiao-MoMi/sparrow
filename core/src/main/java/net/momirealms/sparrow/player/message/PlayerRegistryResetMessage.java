package net.momirealms.sparrow.player.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class PlayerRegistryResetMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "player_registry_reset");
    public static final MessageCodec<FriendlyByteBuf, PlayerRegistryResetMessage> CODEC = RedisMessage.codec(PlayerRegistryResetMessage::write, PlayerRegistryResetMessage::new);

    private final String serverId;

    public PlayerRegistryResetMessage(@NotNull String serverId) {
        this.serverId = serverId;
    }

    private PlayerRegistryResetMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.serverId = ByteBufHelper.readUtf8(buffer, 255);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.serverId, 255);
    }

    @NotNull
    public String serverId() {
        return this.serverId;
    }

    @Override
    @NotNull
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        SparrowPlugin.instance().playerManager().registry.receive(this);
    }
}