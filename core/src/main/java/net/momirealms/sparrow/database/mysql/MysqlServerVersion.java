package net.momirealms.sparrow.database.mysql;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

record MysqlServerVersion(int major, int minor, int patch) {

    /**
     * 解析服务端返回的版本文本, 三段数字必须紧跟在文本开头.
     *
     * @param version 服务端返回的原始版本文本
     * @return 解析结果, 文本不以三段数字开头或数字段超出 int 范围时为 null
     */
    @Nullable
    public static MysqlServerVersion parse(@NotNull String version) {
        int[] segments = new int[3];
        int index = 0;
        for (int i = 0; i < segments.length; i++) {
            if (i != 0) {
                if (index >= version.length() || version.charAt(index) != '.') return null;
                index++;
            }
            int start = index;
            while (index < version.length() && isAsciiDigit(version.charAt(index))) index++;
            if (index == start) return null;
            try {
                segments[i] = Integer.parseInt(version.substring(start, index));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return new MysqlServerVersion(segments[0], segments[1], segments[2]);
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
    public boolean atLeast(@NotNull MysqlServerVersion minimum) {
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
