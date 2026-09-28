package net.momirealms.sparrow.feature.ban;

import net.momirealms.sparrow.util.IpRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BanRecordTest {

    @Test
    void generatedIdsRoundTripThroughParse() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String id = BanRecord.newId();
            assertEquals(BanRecord.ID_LENGTH, id.length());
            assertEquals(id, BanRecord.parseId(BanRecord.ID_PREFIX + id));
            ids.add(id);
        }
        assertTrue(ids.size() > 990);
    }

    @Test
    void parsedIdsIgnoreCase() {
        assertEquals("AB12CD34", BanRecord.parseId("#ab12cd34"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB12CD34", "#AB12CD3", "#AB12CD345", "#AB12CD3I", "#AB12CD3O", "#AB12-D34"})
    void rejectsTextThatIsNotAnId(String input) {
        assertNull(BanRecord.parseId(input));
    }

    @Test
    void activeUntilRevokedOrExpired() {
        BanRecord permanent = record(null, IpRange.parse("1.2.3.*"), 0, 0);
        assertTrue(permanent.permanent());
        assertTrue(permanent.active(Long.MAX_VALUE));
        assertFalse(record(null, IpRange.parse("1.2.3.4"), 1000, 0).active(1000));
        assertTrue(record(null, IpRange.parse("1.2.3.4"), 1000, 0).active(999));
        assertFalse(record(null, IpRange.parse("1.2.3.4"), 0, 500).active(1));
    }

    @Test
    void displaysAccountIpOrBoth() {
        UUID player = UUID.randomUUID();
        assertEquals("Steve", record(player, null, 0, 0).display());
        assertEquals("1.2.*.*", record(null, IpRange.parse("1.2.*.*"), 0, 0).display());
        assertEquals("Steve + 1.2.3.4", record(player, IpRange.parse("1.2.3.4"), 0, 0).display());
    }

    private static BanRecord record(UUID player, IpRange ip, long expiresAt, long revokedAt) {
        return new BanRecord(BanRecord.newId(), player, player == null ? null : "Steve", ip, "", "Admin", "survival", 0, expiresAt, revokedAt, null);
    }
}
