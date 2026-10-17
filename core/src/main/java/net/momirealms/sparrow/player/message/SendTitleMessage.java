package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public final class SendTitleMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "send_title");
    public static final MessageCodec<FriendlyByteBuf, SendTitleMessage> CODEC = RedisMessage.codec(SendTitleMessage::write, SendTitleMessage::new);

    private final UUID playerId;
    private final Component title;
    private final Component subtitle;
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;

    public SendTitleMessage(@NotNull UUID playerId, @NotNull Component title, @NotNull Component subtitle, int fadeIn, int stay, int fadeOut) {
        this.playerId = playerId;
        this.title = title;
        this.subtitle = subtitle;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    private SendTitleMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.title = AdventureHelper.jsonToComponent(ByteBufHelper.readUtf8(buffer, 262144));
        this.subtitle = AdventureHelper.jsonToComponent(ByteBufHelper.readUtf8(buffer, 262144));
        this.fadeIn = buffer.readInt();
        this.stay = buffer.readInt();
        this.fadeOut = buffer.readInt();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        ByteBufHelper.writeUtf8(buffer, AdventureHelper.componentToJson(this.title), 262144);
        ByteBufHelper.writeUtf8(buffer, AdventureHelper.componentToJson(this.subtitle), 262144);
        buffer.writeInt(this.fadeIn);
        buffer.writeInt(this.stay);
        buffer.writeInt(this.fadeOut);
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
        player.connection.sendPacket(new ClientboundBundlePacket(List.of(
                new ClientboundSetTitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(this.title))),
                new ClientboundSetSubtitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(this.subtitle))),
                new ClientboundSetTitlesAnimationPacket(this.fadeIn, this.stay, this.fadeOut)
        )));
    }
}