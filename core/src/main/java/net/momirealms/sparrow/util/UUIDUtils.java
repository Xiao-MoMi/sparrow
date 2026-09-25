package net.momirealms.sparrow.util;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class UUIDUtils {
    private UUIDUtils() {
    }

    /** 解析标准 UUID 或 32 位无连字符的 UUID. */
    @NotNull
    public static UUID fromString(@NotNull String value) {
        if (value.matches("[0-9a-fA-F]{32}")) {
            value = value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16) + "-" + value.substring(16, 20) + "-" + value.substring(20);
        }
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw new IllegalArgumentException("Invalid UUID: " + value);
        return UUID.fromString(value);
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
