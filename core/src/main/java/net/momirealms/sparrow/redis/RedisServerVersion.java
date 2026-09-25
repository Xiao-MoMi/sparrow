package net.momirealms.sparrow.redis;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

record RedisServerVersion(int major, int minor, int patch) {
    private static final String VERSION_FIELD = "redis_version:";

    /**
     * 从 INFO server 的输出里读出版本号.
     *
     * @param info INFO server 的原始文本
     * @return 解析结果, 缺少 redis_version 字段或字段值不以三段数字开头时为 null
     */
    @Nullable
    public static RedisServerVersion parse(@NotNull String info) {
        int field = info.indexOf(VERSION_FIELD);
        if (field < 0) return null;
        int index = field + VERSION_FIELD.length();
        int[] segments = new int[3];
        for (int i = 0; i < segments.length; i++) {
            if (i != 0) {
                if (index >= info.length() || info.charAt(index) != '.') return null;
                index++;
            }
            // 每段只取开头的数字, 段尾的非数字后缀(如 0-rc1 的 -rc1)不参与比较
            int digits = index;
            while (index < info.length() && isAsciiDigit(info.charAt(index))) index++;
            if (index == digits) return null;
            try {
                segments[i] = Integer.parseInt(info.substring(digits, index));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return new RedisServerVersion(segments[0], segments[1], segments[2]);
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    /**
     * 判断是否达到指定的最低版本, 按 major -> minor -> patch 逐段比较.
     *
     * @param minimum 需要达到的最低版本
     * @return 三段都不低于最低版本时为 true
     */
    public boolean atLeast(@NotNull RedisServerVersion minimum) {
        if (this.major != minimum.major) return this.major > minimum.major;
        if (this.minor != minimum.minor) return this.minor > minimum.minor;
        return this.patch >= minimum.patch;
    }

    @NotNull
    @Override
    public String toString() {
        return this.major + "." + this.minor + "." + this.patch;
    }
}
