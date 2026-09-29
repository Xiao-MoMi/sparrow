package net.momirealms.sparrow.proxy.common.message;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.momirealms.sparrow.proxy.common.ProxyPlatform;
import net.momirealms.sparrow.proxy.common.logger.ProxyLogger;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisconnectMessageTest {
    // 和后端 DisconnectMessageTest 使用同一段字节, 保证两端字段顺序一致
    private static final String ENCODED = "000123456789abcdeffedcba98765432100e7b2274657874223a22627965227d";

    @Test
    void decodesTheLayoutTheBackendWrites() {
        RecordingPlatform platform = new RecordingPlatform();
        DisconnectMessage message = DisconnectMessage.codec(platform).decode(Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(ENCODED)));
        message.handle();
        assertEquals(new UUID(0x0123456789abcdefL, 0xfedcba9876543210L), platform.player);
        assertEquals("{\"text\":\"bye\"}", platform.reason);
    }

    private static final class RecordingPlatform implements ProxyPlatform {
        private UUID player;
        private String reason;

        @Override
        public Path dataFolderPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProxyLogger logger() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disconnect(UUID player, String jsonReason) {
            this.player = player;
            this.reason = jsonReason;
        }
    }
}
