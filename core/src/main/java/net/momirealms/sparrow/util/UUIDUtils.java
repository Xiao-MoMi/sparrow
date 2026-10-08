package net.momirealms.sparrow.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.regex.Pattern;

public final class UUIDUtils {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern PATTERN = Pattern.compile(
            "[0-9a-fA-F]{32}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );

    private UUIDUtils() {
    }

    @NotNull
    public static UUID createV7() {
        long most = (System.currentTimeMillis() << 16) | 0x7000L | RANDOM.nextInt(1 << 12);
        long least = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(most, least);
    }

    /** 解析标准 UUID 或 32 位无连字符的 UUID. */
    @NotNull
    public static UUID fromString(@NotNull String value) {
        UUID uuid = parse(value);
        if (uuid == null) {
            throw new IllegalArgumentException("Invalid UUID: " + value);
        }
        return uuid;
    }

    /** 解析标准 UUID 或 32 位无连字符的 UUID, 格式不符时返回 null. */
    @Nullable
    public static UUID parse(@NotNull String value) {
        if (!PATTERN.matcher(value).matches()) return null;
        if (value.length() == 36) return UUID.fromString(value);
        return new UUID(Long.parseUnsignedLong(value.substring(0, 16), 16), Long.parseUnsignedLong(value.substring(16), 16));
    }

    public static byte @NotNull [] toBytes(@NotNull UUID uuid) {
        // ByteBuffer 默认使用大端序, 高 64 位在前、低 64 位在后.
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    @NotNull
    public static UUID fromBytes(byte @NotNull [] bytes) {
        if (bytes.length != 16) throw new IllegalArgumentException("UUID requires 16 bytes, got " + bytes.length);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
