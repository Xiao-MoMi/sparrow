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
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.MessageText;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public final class SendTitleMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "send_title");
    public static final MessageCodec<FriendlyByteBuf, SendTitleMessage> CODEC = RedisMessage.codec(SendTitleMessage::write, SendTitleMessage::new);

    private final UUID playerId;
    private final MessageText title;
    private final MessageText subtitle;
    private final int fadeIn;
    private final int stay;
    private final int fadeOut;

    public SendTitleMessage(@NotNull UUID playerId, @NotNull Component title, @NotNull Component subtitle, int fadeIn, int stay, int fadeOut) {
        this.playerId = playerId;
        this.title = new MessageText(title);
        this.subtitle = new MessageText(subtitle);
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    public SendTitleMessage(@NotNull UUID playerId, @NotNull String title, @NotNull String subtitle, int fadeIn, int stay, int fadeOut) {
        this.playerId = playerId;
        this.title = new MessageText(title);
        this.subtitle = new MessageText(subtitle);
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    private SendTitleMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.title = new MessageText(buffer);
        this.subtitle = new MessageText(buffer);
        this.fadeIn = buffer.readInt();
        this.stay = buffer.readInt();
        this.fadeOut = buffer.readInt();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
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
    protected void handle() {
        this.sendLocally();
    }

    // 在当前执行器上解析并发送, 目标不在本服时返回 false.
    public boolean sendLocally() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer player = plugin.playerManager().getOnlinePlayer(this.playerId);
        if (player == null) return false;
        this.send(player);
        return true;
    }

    private void send(BukkitSparrowPlayer player) {
        player.connection.sendPacket(new ClientboundBundlePacket(List.of(
                new ClientboundSetTitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(this.title.resolve(player)))),
                new ClientboundSetSubtitleTextPacket(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(this.subtitle.resolve(player)))),
                new ClientboundSetTitlesAnimationPacket(this.fadeIn, this.stay, this.fadeOut)
        )));
    }
}