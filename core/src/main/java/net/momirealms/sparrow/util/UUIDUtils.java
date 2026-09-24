package net.momirealms.sparrow.util;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class UUIDUtils {
    private UUIDUtils() {
    }

    public static byte @NotNull [] toBytes(@NotNull UUID uuid) {
        // ByteBuffer 默认使用大端序, 高 64 位在前、低 64 位在后.
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }

    @NotNull
    public static UUID fromBytes(byte @NotNull [] bytes) {
        if (bytes.length != 16) throw new IllegalArgumentException("UUID requires 16 bytes, got " + bytes.length);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
