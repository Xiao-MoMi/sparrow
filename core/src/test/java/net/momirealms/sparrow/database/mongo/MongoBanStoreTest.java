package net.momirealms.sparrow.database.mongo;

import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MongoBanStoreTest {

    @Test
    void saveBanRejectsRecordWithoutTarget() {
        MongoBanStore store = new MongoBanStore(() -> null, Runnable::run, mock(PluginLogger.class), "");
        BanRecord banRecord = new BanRecord("12345678", null, null, null, "reason", "operator", "server", 1, 0, 0, null);

        assertThrows(IllegalArgumentException.class, () -> store.saveBan(banRecord));
    }
}
