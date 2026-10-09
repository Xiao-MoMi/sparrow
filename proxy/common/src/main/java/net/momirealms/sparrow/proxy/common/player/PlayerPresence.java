package net.momirealms.sparrow.proxy.common.player;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

public record PlayerPresence(@NotNull UUID uuid, @NotNull String name, @NotNull String server, @NotNull Locale locale) {

    @NotNull
    public static PlayerPresence read(@NotNull UUID uuid, @NotNull ByteBuf buffer) {
        return new PlayerPresence(uuid, ByteBufHelper.readUtf8(buffer, 64), ByteBufHelper.readUtf8(buffer, 32767), Locale.forLanguageTag(ByteBufHelper.readUtf8(buffer, 128)));
    }

    public void write(@NotNull ByteBuf buffer) {
        ByteBufHelper.writeUtf8(buffer, this.name, 64);
        ByteBufHelper.writeUtf8(buffer, this.server, 32767);
        ByteBufHelper.writeUtf8(buffer, this.locale.toLanguageTag(), 128);
    }
}
