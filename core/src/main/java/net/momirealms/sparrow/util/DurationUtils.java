package net.momirealms.sparrow.util;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUtils {
    private static final Pattern PART = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(mo|ms|[ywdhms])");
    private static final long[] FORMAT_SECONDS = {86400, 3600, 60, 1};
    private static final String[] FORMAT_UNITS = {"d", "h", "m", "s"};

    private DurationUtils() {
    }

    /**
     * 解析带单位的正时长, 支持 y/mo/w/d/h/m/s/ms, 可以组合 (1d12h) 也可以带小数 (1.5h).
     * 一个月按 30 天, 一年按 365 天计算, 结果按毫秒四舍五入.
     *
     * @throws IllegalArgumentException 当格式错误或结果不为正时
     * @throws ArithmeticException 当结果超出 long 毫秒范围时
     */
    @NotNull
    public static Duration parsePositive(@NotNull String value) {
        Matcher matcher = PART.matcher(value);
        BigDecimal millis = BigDecimal.ZERO;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) throw new IllegalArgumentException("Invalid duration: " + value);
            long multiplier = switch (matcher.group(2)) {
                case "y" -> 31_536_000_000L;
                case "mo" -> 2_592_000_000L;
                case "w" -> 604_800_000L;
                case "d" -> 86_400_000L;
                case "h" -> 3_600_000L;
                case "m" -> 60_000L;
                case "s" -> 1_000L;
                default -> 1L;
            };
            millis = millis.add(new BigDecimal(matcher.group(1)).multiply(BigDecimal.valueOf(multiplier)));
            end = matcher.end();
        }
        long result = millis.setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (end != value.length() || result <= 0) throw new IllegalArgumentException("Invalid positive duration: " + value);
        return Duration.ofMillis(result);
    }

    /** 按 {@link #parsePositive} 能读回的写法输出时长, 精确到秒并省略为 0 的单位, 例如 1d2h30m. 不足一秒时输出 0s. */
    @NotNull
    public static String format(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds == 0) return "0s";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < FORMAT_SECONDS.length; i++) {
            long amount = seconds / FORMAT_SECONDS[i];
            if (amount > 0) {
                builder.append(amount).append(FORMAT_UNITS[i]);
                seconds %= FORMAT_SECONDS[i];
            }
        }
        return builder.toString();
    }
}
