package net.momirealms.sparrow.teleport.codec;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;

public interface TeleportCodec {

    @NotNull
    SparrowKey type();

    @NotNull
    CompoundTag toNbt();
}