package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.PacketBossEvent;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class ShowBossBarMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "show_boss_bar");
    public static final MessageCodec<FriendlyByteBuf, ShowBossBarMessage> CODEC = RedisMessage.codec(ShowBossBarMessage::write, ShowBossBarMessage::new);

    private final UUID playerId;
    private final UUID bossBarId;
    private final Component title;
    private final float progress;
    private final BossBarColor color;
    private final BossBarOverlay overlay;

    public ShowBossBarMessage(@NotNull UUID playerId, @NotNull UUID bossBarId, @NotNull Component title, float progress, @NotNull BossBarColor color, @NotNull BossBarOverlay overlay) {
        this.playerId = playerId;
        this.bossBarId = bossBarId;
        this.title = title;
        this.progress = progress;
        this.color = color;
        this.overlay = overlay;
    }

    private ShowBossBarMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.bossBarId = buffer.readUUID();
        this.title = AdventureHelper.jsonToComponent(ByteBufHelper.readUtf8(buffer, 262144));
        this.progress = buffer.readFloat();
        this.color = BossBarColor.valueOf(ByteBufHelper.readUtf8(buffer, 64));
        this.overlay = BossBarOverlay.valueOf(ByteBufHelper.readUtf8(buffer, 64));
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        buffer.writeUUID(this.bossBarId);
        ByteBufHelper.writeUtf8(buffer, AdventureHelper.componentToJson(this.title), 262144);
        buffer.writeFloat(this.progress);
        ByteBufHelper.writeUtf8(buffer, this.color.name(), 64);
        ByteBufHelper.writeUtf8(buffer, this.overlay.name(), 64);
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
        PacketBossEvent event = new PacketBossEvent(this.bossBarId, CraftChatMessage.fromJSON(AdventureHelper.componentToJson(this.title)), this.color, this.overlay);
        event.setProgress(this.progress);
        player.connection.sendPacket(ClientboundBossEventPacket.createAddPacket(event));
    }
}