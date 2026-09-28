package net.momirealms.sparrow.util;

import org.jetbrains.annotations.NotNull;

import java.net.Inet4Address;
import java.net.InetAddress;

/**
 * IPv4 闭区间, 单个 IP 是起止相同的区间, 通配 {@code 1.2.*.*} 覆盖对应的整段.
 * 地址按无符号 32 位整数保存在 long 中, 取值 0 ~ 2^32-1, 直接比较大小即可判断包含关系.
 *
 * @param start 区间起点
 * @param end 区间终点
 */
public record IpRange(long start, long end) {
    public static final long NONE = -1; // 非 IPv4 地址, 任何区间都不包含它

    @NotNull
    public static IpRange of(long address) {
        return new IpRange(address, address);
    }

    /**
     * 当输入来源为玩家ID或IP时, 判断输入是否应按 IP 处理.
     */
    public static boolean looksLikeIp(@NotNull String input) {
        int length = input.length();
        for (int i = 0; i < length; i++) {
            char c = input.charAt(i);
            if (c == '.' || c == '*') return true;
        }
        return false;
    }

    /**
     * 解析 IPv4 地址, 或末尾若干段为 {@code *} 的通配地址.
     *
     * @param input 待解析的文本
     * @return 对应的 IP 段
     * @throws IllegalArgumentException 当文本不是合法的 IPv4 或通配地址时
     */
    @NotNull
    public static IpRange parse(@NotNull String input) {
        // 手动解析, 非字面量不会触发 DNS 查询
        String[] parts = input.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Invalid IPv4 address: " + input);
        long start = 0;
        long end = 0;
        boolean wildcard = false;
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.equals("*")) {
                wildcard = true;
                start <<= 8;
                end = end << 8 | 0xFF;
                continue;
            }
            // 通配段之后不能再出现具体数字
            if (wildcard) throw new IllegalArgumentException("Wildcards must be trailing: " + input);
            int octet = parseOctet(part, input);
            start = start << 8 | octet;
            end = end << 8 | octet;
        }
        return new IpRange(start, end);
    }

    // 平台给出的地址, 不是 IPv4 时返回 NONE
    public static long address(@NotNull InetAddress address) {
        if (!(address instanceof Inet4Address)) return NONE;
        byte[] raw = address.getAddress();
        return (raw[0] & 0xFFL) << 24 | (raw[1] & 0xFFL) << 16 | (raw[2] & 0xFFL) << 8 | raw[3] & 0xFFL;
    }

    @NotNull
    public static String format(long address) {
        return (address >>> 24 & 0xFF) + "." + (address >>> 16 & 0xFF) + "." + (address >>> 8 & 0xFF) + "." + (address & 0xFF);
    }

    public boolean contains(long address) {
        return this.start <= address && address <= this.end;
    }

    public boolean single() {
        return this.start == this.end;
    }

    // 单个 IP 输出地址, 通配段输出 *, 例如 1.2.*.*
    @Override
    @NotNull
    public String toString() {
        StringBuilder builder = new StringBuilder(15);
        for (int shift = 24; shift >= 0; shift -= 8) {
            long start = this.start >>> shift & 0xFF;
            if (shift < 24) {
                builder.append('.');
            }
            if (start == (this.end >>> shift & 0xFF)) {
                builder.append(start);
            } else {
                builder.append('*');
            }
        }
        return builder.toString();
    }

    private static int parseOctet(String part, String input) {
        if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + input);
        }
        int value = Integer.parseInt(part);
        if (value > 255) throw new IllegalArgumentException("Invalid IPv4 address: " + input);
        return value;
    }
}
