package net.momirealms.sparrow.util;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUtils {
    private static final Pattern PART = Pattern.compile("([0-9]+)(ms|[dhms])");

    private DurationUtils() {
    }

    /** 解析带单位的正时长, 支持 d/h/m/s/ms 及 1m30s 这样的组合. */
    @NotNull
    public static Duration parsePositive(@NotNull String value) {
        Matcher matcher = PART.matcher(value);
        long millis = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) throw new IllegalArgumentException("Invalid duration: " + value);
            long multiplier = switch (matcher.group(2)) {
                case "d" -> 86400000;
                case "h" -> 3600000;
                case "m" -> 60000;
                case "s" -> 1000;
                default -> 1;
            };
            millis = Math.addExact(millis, Math.multiplyExact(Long.parseLong(matcher.group(1)), multiplier));
            end = matcher.end();
        }
        if (end != value.length() || millis <= 0) throw new IllegalArgumentException("Invalid positive duration: " + value);
        return Duration.ofMillis(millis);
    }
}
