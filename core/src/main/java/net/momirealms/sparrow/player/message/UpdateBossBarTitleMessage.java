package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
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

public final class UpdateBossBarTitleMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "update_boss_bar_title");
    public static final MessageCodec<FriendlyByteBuf, UpdateBossBarTitleMessage> CODEC = RedisMessage.codec(UpdateBossBarTitleMessage::write, UpdateBossBarTitleMessage::new);

    private final UUID playerId;
    private final UUID bossBarId;
    private final Component title;

    public UpdateBossBarTitleMessage(@NotNull UUID playerId, @NotNull UUID bossBarId, @NotNull Component title) {
        this.playerId = playerId;
        this.bossBarId = bossBarId;
        this.title = title;
    }

    private UpdateBossBarTitleMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.bossBarId = buffer.readUUID();
        this.title = AdventureHelper.jsonToComponent(ByteBufHelper.readUtf8(buffer, 262144));
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        buffer.writeUUID(this.bossBarId);
        ByteBufHelper.writeUtf8(buffer, AdventureHelper.componentToJson(this.title), 262144);
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
        PacketBossEvent event = new PacketBossEvent(this.bossBarId);
        event.setName(CraftChatMessage.fromJSON(AdventureHelper.componentToJson(this.title)));
        player.connection.sendPacket(ClientboundBossEventPacket.createUpdateNamePacket(event));
    }
}