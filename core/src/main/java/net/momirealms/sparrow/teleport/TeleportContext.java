package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.Tag;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TeleportContext {
    public static final String PLAYER_UUID = "player_uuid"; // 被传送玩家的 UUID, 提交时写入
    public static final String PLAYER_NAME = "player_name"; // 被传送玩家的名字, 提交时写入
    public static final String UNSAFE = "unsafe";           // 布尔标记, 调用方用它要求处理器跳过安全类检查, 由处理器自行读取

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

    void bindPlayer(@NotNull BukkitSparrowPlayer player) {
        this.tag.putUUID(PLAYER_UUID, player.uniqueId());
        this.tag.putString(PLAYER_NAME, player.name());
    }
}