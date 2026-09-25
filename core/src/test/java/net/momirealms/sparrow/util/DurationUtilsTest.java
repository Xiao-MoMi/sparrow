package net.momirealms.sparrow.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DurationUtilsTest {
    @ParameterizedTest
    @CsvSource({"1ms,1", "5m,300000", "1d2h3m4s5ms,93784005", "24h,86400000"})
    void parsesWallClockDurations(String value, long expected) {
        assertEquals(expected, DurationUtils.parsePositive(value).toMillis());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0s", "-1s", "3", "1m nope", "9223372036854775807d"})
    void rejectsMissingUnitsNonPositiveDurationsAndOverflow(String value) {
        assertThrows(RuntimeException.class, () -> DurationUtils.parsePositive(value));
    }
}
