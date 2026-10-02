package net.momirealms.sparrow.util;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// 按服务器时区格式化时间, 完整时间带时区偏移.
public final class DateTimeUtils {
    private static final DateTimeFormatter FULL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm").withZone(ZoneId.systemDefault());

    private DateTimeUtils() {
    }

    @NotNull
    public static String shortTime(long millis) {
        return SHORT_TIME.format(Instant.ofEpochMilli(millis));
    }

    @NotNull
    public static String fullTime(long millis) {
        return FULL_TIME.format(Instant.ofEpochMilli(millis));
    }
}
