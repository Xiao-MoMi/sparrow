package net.momirealms.sparrow.player.message;

import net.kyori.adventure.sound.Sound;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class PlaySoundMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "play_sound");
    public static final MessageCodec<FriendlyByteBuf, PlaySoundMessage> CODEC = RedisMessage.codec(PlaySoundMessage::write, PlaySoundMessage::new);

    private final UUID playerId;
    private final String soundKey;
    private final Sound.Source source;
    private final float volume;
    private final float pitch;
    private final long seed;

    public PlaySoundMessage(@NotNull UUID playerId, @NotNull Sound sound) {
        this.playerId = playerId;
        this.soundKey = sound.name().namespace() + ":" + sound.name().value();
        this.source = sound.source();
        this.volume = sound.volume();
        this.pitch = sound.pitch();
        this.seed = sound.seed().orElseGet(() -> ThreadLocalRandom.current().nextLong());
    }

    private PlaySoundMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.soundKey = ByteBufHelper.readUtf8(buffer, 32767);
        this.source = Sound.Source.valueOf(ByteBufHelper.readUtf8(buffer, 64));
        this.volume = buffer.readFloat();
        this.pitch = buffer.readFloat();
        this.seed = buffer.readLong();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        ByteBufHelper.writeUtf8(buffer, this.soundKey, 32767);
        ByteBufHelper.writeUtf8(buffer, this.source.name(), 64);
        buffer.writeFloat(this.volume);
        buffer.writeFloat(this.pitch);
        buffer.writeLong(this.seed);
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
        Holder<SoundEvent> event = Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.parse(this.soundKey)));
        player.connection.sendPacket(new ClientboundSoundEntityPacket(event, AdventureHelper.toNmsSoundSource(this.source), player.nmsPlayer, this.volume, this.pitch, this.seed));
    }
}