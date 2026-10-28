package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public final class SendTitleMessage extends AbstractPlayerTextMessage {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "send_title");
    public static final MessageCodec<FriendlyByteBuf, SendTitleMessage> CODEC = RedisMessage.codec(SendTitleMessage::write, SendTitleMessage::new);

    private final Text title;
    private final Text subtitle;
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;

    public SendTitleMessage(@NotNull UUID playerId, @NotNull Component title, @NotNull Component subtitle, int fadeIn, int stay, int fadeOut) {
        super(playerId);
        this.title = new Text(title);
        this.subtitle = new Text(subtitle);
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    public SendTitleMessage(@NotNull UUID playerId, @NotNull String title, @NotNull String subtitle, int fadeIn, int stay, int fadeOut) {
        super(playerId);
        this.title = new Text(title);
        this.subtitle = new Text(subtitle);
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    private SendTitleMessage(@NotNull UUID playerId, @NotNull Text title, @NotNull Text subtitle, int fadeIn, int stay, int fadeOut) {
        super(playerId);
        this.title = title;
        this.subtitle = subtitle;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    @NotNull
    public static SendTitleMessage translated(@NotNull UUID playerId, @NotNull Component title, @NotNull Component subtitle, int fadeIn, int stay, int fadeOut) {
        return new SendTitleMessage(playerId, Text.translated(title), Text.translated(subtitle), fadeIn, stay, fadeOut);
    }

    private SendTitleMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.title = new Text(buffer);
        this.subtitle = new Text(buffer);
        this.fadeIn = buffer.readInt();
        this.stay = buffer.readInt();
        this.fadeOut = buffer.readInt();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        this.title.write(buffer);
        this.subtitle.write(buffer);
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
    protected void send(@NotNull BukkitSparrowPlayer player) {
        player.connection.sendPacket(new ClientboundBundlePacket(List.of(
                new ClientboundSetTitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(this.title.resolve(player)))),
                new ClientboundSetSubtitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(this.subtitle.resolve(player)))),
                new ClientboundSetTitlesAnimationPacket(this.fadeIn, this.stay, this.fadeOut)
        )));
    }
}