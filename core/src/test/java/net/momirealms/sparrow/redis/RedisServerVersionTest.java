package net.momirealms.sparrow.redis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RedisServerVersionTest {
    @Test
    void parsesInfoAndChecksMinimum() {
        RedisServerVersion minimum = new RedisServerVersion(7, 2, 4);
        assertTrue(RedisServerVersion.parse("# Server\r\nredis_version:7.2.4\r\n").atLeast(minimum));
        assertTrue(RedisServerVersion.parse("redis_version:8.0.0-rc1").atLeast(minimum));
        assertFalse(RedisServerVersion.parse("redis_version:7.2.3").atLeast(minimum));
        assertNull(RedisServerVersion.parse("# Server"));
        assertNull(RedisServerVersion.parse("redis_version:7.x.4"));
    }
}
