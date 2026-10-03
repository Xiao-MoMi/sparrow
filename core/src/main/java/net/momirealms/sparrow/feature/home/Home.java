package net.momirealms.sparrow.feature.home;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

public record Home(@NotNull UUID id,
                   @NotNull UUID owner,
                   @NotNull String name,
                   @NotNull String server,
                   @NotNull WorldLocation location,
                   long createdAt,
                   long updatedAt) {
    public static final int MAX_NAME_LENGTH = 32;

    @NotNull
    public String key() {
        return key(this.name);
    }

    @NotNull
    public static String key(@NotNull String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public void write(@NotNull FriendlyByteBuf buffer) {
        buffer.writeUUID(this.id);
        buffer.writeUUID(this.owner);
        ByteBufHelper.writeUtf8(buffer, this.name, MAX_NAME_LENGTH);
        ByteBufHelper.writeUtf8(buffer, this.server, 255);
        ByteBufHelper.writeUtf8(buffer, this.location.world(), 255);
        buffer.writeDouble(this.location.x()).writeDouble(this.location.y()).writeDouble(this.location.z());
        buffer.writeFloat(this.location.yaw()).writeFloat(this.location.pitch());
        buffer.writeLong(this.createdAt).writeLong(this.updatedAt);
    }

    @NotNull
    public static Home read(@NotNull FriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        UUID owner = buffer.readUUID();
        String name = ByteBufHelper.readUtf8(buffer, MAX_NAME_LENGTH);
        String server = ByteBufHelper.readUtf8(buffer, 255);
        WorldLocation location = new WorldLocation(ByteBufHelper.readUtf8(buffer, 255), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readFloat());
        return new Home(id, owner, name, server, location, buffer.readLong(), buffer.readLong());
    }
}
