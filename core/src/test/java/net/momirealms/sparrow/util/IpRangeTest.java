package net.momirealms.sparrow.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.*;

class IpRangeTest {

    private static long ip(String literal) throws UnknownHostException {
        return IpRange.address(InetAddress.getByName(literal));
    }

    @Test
    void singleIpv4ContainsOnlyItself() throws UnknownHostException {
        IpRange range = IpRange.parse("1.2.3.4");
        assertTrue(range.single());
        assertTrue(range.contains(ip("1.2.3.4")));
        assertFalse(range.contains(ip("1.2.3.5")));
        assertEquals("1.2.3.4", range.toString());
        assertEquals(range, IpRange.of(ip("1.2.3.4")));
        assertEquals("1.2.3.4", IpRange.format(ip("1.2.3.4")));
    }

    @Test
    void highAddressesStayUnsigned() throws UnknownHostException {
        long address = ip("255.255.255.255");
        assertEquals(0xFFFFFFFFL, address);
        assertEquals("255.255.255.255", IpRange.format(address));
        assertTrue(IpRange.parse("255.*.*.*").contains(address));
        assertFalse(IpRange.parse("127.*.*.*").contains(address));
    }

    @Test
    void wildcardCoversTrailingOctets() throws UnknownHostException {
        IpRange range = IpRange.parse("192.168.*.*");
        assertFalse(range.single());
        assertTrue(range.contains(ip("192.168.0.0")));
        assertTrue(range.contains(ip("192.168.255.255")));
        assertFalse(range.contains(ip("192.169.0.0")));
        assertFalse(range.contains(ip("192.167.255.255")));
        assertEquals("192.168.*.*", range.toString());
        assertEquals("*.*.*.*", IpRange.parse("*.*.*.*").toString());
    }

    @Test
    void ipv6IsNeverContained() throws UnknownHostException {
        long address = ip("2001:db8::1");
        assertEquals(IpRange.NONE, address);
        assertFalse(IpRange.parse("*.*.*.*").contains(address));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1.2.3", "1.2.3.4.5", "256.1.1.1", "1.2.3.-1", "1.2.3.0/24", "1.*.3.*", "*.2.3.4", "example.com", "2001:db8::1", "1.2.3.a", "1.2.3.**"})
    void rejectsInvalidInput(String input) {
        assertThrows(IllegalArgumentException.class, () -> IpRange.parse(input));
    }
}
