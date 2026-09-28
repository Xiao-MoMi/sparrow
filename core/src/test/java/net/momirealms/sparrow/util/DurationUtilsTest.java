package net.momirealms.sparrow.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DurationUtilsTest {
    @ParameterizedTest
    @CsvSource({"1ms,1", "5m,300000", "1d2h3m4s5ms,93784005", "24h,86400000", "1.5h,5400000", "0.5s,500", "1w,604800000", "1mo,2592000000", "1y,31536000000", "1y1mo1w,34732800000"})
    void parsesWallClockDurations(String value, long expected) {
        assertEquals(expected, DurationUtils.parsePositive(value).toMillis());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0s", "-1s", "3", "1m nope", "9223372036854775807d", "0.0001ms", "1.h", ".5h", "1mon"})
    void rejectsMissingUnitsNonPositiveDurationsAndOverflow(String value) {
        assertThrows(RuntimeException.class, () -> DurationUtils.parsePositive(value));
    }

    @ParameterizedTest
    @CsvSource({"0,0s", "999,0s", "90061000,1d1h1m1s", "3600000,1h", "-5000,0s"})
    void formatsWholeSecondsWithoutZeroUnits(long millis, String expected) {
        assertEquals(expected, DurationUtils.format(millis));
    }
}
