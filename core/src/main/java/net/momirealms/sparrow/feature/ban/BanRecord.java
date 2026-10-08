package net.momirealms.sparrow.feature.ban;

import net.momirealms.sparrow.util.IpRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * 一条封禁记录. 玩家与 IP 至少有一项, 两项都有时为账号加 IP 的封禁, 登录时任一命中即拒绝. 时间均为 Unix 毫秒.
 *
 * @param id 随机处罚 ID, 不含 # 前缀
 * @param player 被封禁玩家的 UUID, 纯 IP 封禁时为 null
 * @param playerName 封禁时玩家使用的名字, 只用于展示
 * @param ip 被封禁的 IP 段, 纯玩家封禁时为 null
 * @param reason 封禁原因, 空字符串表示未提供
 * @param operatorName 执行人名字
 * @param server 执行封禁的服务器 ID
 * @param createdAt 封禁时间
 * @param expiresAt 到期时间, 0 表示永久
 * @param revokedAt 解封或被新封禁覆盖的时间, 0 表示未撤销
 * @param revokedBy 撤销人名字, 未撤销时为 null
 */
public record BanRecord(@NotNull String id,
                        @Nullable UUID player,
                        @Nullable String playerName,
                        @Nullable IpRange ip,
                        @NotNull String reason,
                        @NotNull String operatorName,
                        @NotNull String server,
                        long createdAt,
                        long expiresAt,
                        long revokedAt,
                        @Nullable String revokedBy) {
    public static final int MAX_REASON_LENGTH = 256; // 与聊天栏的输入上限相同
    public static final int ID_LENGTH = 8;
    public static final char ID_PREFIX = '#';
    // Crockford Base32, 去掉了容易看错的 I L O U
    private static final char[] ID_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @NotNull
    public static String newId() {
        char[] id = new char[ID_LENGTH];
        for (int i = 0; i < ID_LENGTH; i++) {
            id[i] = ID_ALPHABET[RANDOM.nextInt(ID_ALPHABET.length)];
        }
        return new String(id);
    }

    /**
     * 解析带 # 前缀的处罚 ID, 字母不区分大小写.
     *
     * @return 大写且不含前缀的 ID, 不是处罚 ID 时为 null
     */
    @Nullable
    public static String parseId(@NotNull String input) {
        if (input.length() != ID_LENGTH + 1 || input.charAt(0) != ID_PREFIX) {
            return null;
        }
        String id = input.substring(1).toUpperCase(Locale.ROOT);
        for (int i = 0; i < ID_LENGTH; i++) {
            if (Arrays.binarySearch(ID_ALPHABET, id.charAt(i)) < 0) {
                return null;
            }
        }
        return id;
    }

    public boolean permanent() {
        return this.expiresAt == 0;
    }

    public boolean active(long now) {
        return this.revokedAt == 0 && (this.expiresAt == 0 || this.expiresAt > now);
    }

    // 玩家名, IP, 或 "玩家名 + IP"
    @NotNull
    public String display() {
        if (this.ip == null) {
            return String.valueOf(this.playerName);
        }
        if (this.player == null) {
            return this.ip.toString();
        }
        return this.playerName + " + " + this.ip;
    }
}
