package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.Tag;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.util.Dimensions;
import net.momirealms.sparrow.util.ServerLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TeleportContext {
    public static final String PLAYER_UUID = "player_uuid"; // 被传送玩家的 UUID, 提交时写入
    public static final String PLAYER_NAME = "player_name"; // 被传送玩家的名字, 提交时写入
    public static final String UNSAFE = "unsafe";           // 布尔标记, FindSafeLoc 搜索无结果时允许回退到输入原点
    public static final String DIMENSIONS = "dimensions";   // 提交时采集的玩家碰撞箱尺寸和眼高
    private static final String DEPARTURE_LOCATION = "departure_location";
    private static final String SOURCE_CONNECTED_AT = "source_connected_at";
    private static final String DEPARTED_AT = "departed_at";

    private final CompoundTag tag;

    public TeleportContext() {
        this(new CompoundTag());
    }

    public TeleportContext(@NotNull CompoundTag tag) {
        this.tag = tag;
    }

    @NotNull
    public static TeleportContext fromNbt(@NotNull CompoundTag tag) {
        return new TeleportContext(tag);
    }

    @NotNull
    public TeleportContext put(@NotNull String key, @NotNull Tag value) {
        this.tag.put(key, value);
        return this;
    }

    @Nullable
    public Tag get(@NotNull String key) {
        return this.tag.get(key);
    }

    public void remove(@NotNull String key) {
        this.tag.remove(key);
    }

    @NotNull
    public CompoundTag toNbt() {
        return this.tag;
    }

    @NotNull
    public Dimensions getDimensions() {
        if (!(this.tag.get(DIMENSIONS) instanceof CompoundTag dimensions)) {
            throw new IllegalStateException("Teleport context does not contain player dimensions");
        }
        return Dimensions.fromNbt(dimensions);
    }

    void bindPlayer(@NotNull BukkitSparrowPlayer player) {
        this.tag.putUUID(PLAYER_UUID, player.uniqueId());
        this.tag.putString(PLAYER_NAME, player.name());
        this.tag.put(DIMENSIONS, Dimensions.from(player.nmsPlayer).toNbt());
    }

    void captureDeparture(@NotNull BukkitSparrowPlayer player) {
        this.tag.put(DEPARTURE_LOCATION, player.getServerLocation().toNbt());
        this.tag.putLong(SOURCE_CONNECTED_AT, player.connection.connectedAt());
        this.tag.putLong(DEPARTED_AT, System.currentTimeMillis());
    }

    @NotNull
    ServerLocation getDepartureLocation() {
        return ServerLocation.fromNbt((CompoundTag) this.tag.get(DEPARTURE_LOCATION));
    }

    long getSourceConnectedAt() {
        return this.tag.getLong(SOURCE_CONNECTED_AT);
    }

    long getDepartedAt() {
        return this.tag.getLong(DEPARTED_AT);
    }
}