package net.momirealms.sparrow.redis.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisconnectMessageTest {
    // 和代理端 DisconnectMessageTest 使用同一段字节, 保证两端字段顺序一致
    private static final String ENCODED = "000123456789abcdeffedcba98765432100e7b2274657874223a22627965227d";

    @Test
    void encodesTheLayoutTheProxyReads() {
        DisconnectMessage message = new DisconnectMessage(new UUID(0x0123456789abcdefL, 0xfedcba9876543210L), "{\"text\":\"bye\"}");
        message.setTargetServer("");
        ByteBuf buffer = Unpooled.buffer();
        DisconnectMessage.CODEC.encode(buffer, message);
        assertEquals(ENCODED, ByteBufUtil.hexDump(buffer));
    }
}
