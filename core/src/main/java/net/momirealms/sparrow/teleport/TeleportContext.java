package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class TeleportContext {
    public static final String PLAYER_UUID = "player_uuid";
    public static final String PLAYER_NAME = "player_name";
    public static final String UNSAFE = "unsafe";
    public static final String DIMENSIONS = "dimensions";

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
    public TeleportDimensions getDimensions() {
        if (!(this.tag.get(DIMENSIONS) instanceof CompoundTag dimensions)) {
            throw new IllegalStateException("Teleport context does not contain player dimensions");
        }
        return TeleportDimensions.fromNbt(dimensions);
    }

    void bindPlayer(@NotNull UUID playerId, @NotNull String playerName) {
        this.tag.putUUID(PLAYER_UUID, playerId);
        this.tag.putString(PLAYER_NAME, playerName);
    }
}
