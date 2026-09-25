package net.momirealms.sparrow.database.mysql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MysqlServerVersionTest {
    @Test
    void parsesServerSuffixAndChecksMinimum() {
        MysqlServerVersion minimum = new MysqlServerVersion(8, 0, 0);
        assertTrue(MysqlServerVersion.parse("8.0.0-commercial").atLeast(minimum));
        assertTrue(MysqlServerVersion.parse("9.4.0").atLeast(minimum));
        assertFalse(MysqlServerVersion.parse("5.7.44").atLeast(minimum));
        assertNull(MysqlServerVersion.parse("unknown"));
    }
}
