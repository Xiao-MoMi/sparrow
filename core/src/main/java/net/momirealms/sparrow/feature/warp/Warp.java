package net.momirealms.sparrow.feature.warp;

import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * 一个 warp 点.
 * 改名后 id 保持不变, 重名按 {@link #key(String)} 忽略大小写判断.
 *
 * @param creator 创建者, 由控制台创建时为 null
 */
public record Warp(@NotNull UUID id,
                   @NotNull String name,
                   @NotNull String description,
                   @NotNull String server,
                   @NotNull WorldLocation location,
                   @Nullable UUID creator,
                   long createdAt,
                   long updatedAt) {
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_DESCRIPTION_LENGTH = 256;

    @NotNull
    public String key() {
        return key(this.name);
    }

    // 查找和重名判断使用的名称键
    @NotNull
    public static String key(@NotNull String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
