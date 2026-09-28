package net.momirealms.sparrow.plugin.command.feature;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.PlayerRef;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.util.IpRange;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

// 查询类命令共用的玩家、UUID 与 IP 解析, 只使用集群名单和本插件数据库
final class LookupSupport {
    private static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}");

    private LookupSupport() {
    }

    // 玩家名里不会出现这些字符, 出现时按 IP 处理
    static boolean looksLikeIp(@NotNull String input) {
        int length = input.length();
        for (int i = 0; i < length; i++) {
            switch (input.charAt(i)) {
                case '.', '*' -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    // 32 位或 36 位 UUID, 其他文本返回 null
    @Nullable
    static UUID parseUuid(@NotNull String input) {
        if (!UUID_PATTERN.matcher(input).matches()) return null;
        String hex = input.replace("-", "");
        return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
    }

    /**
     * 按玩家名或 UUID 解析数据库中存在的玩家. 在线玩家的名字忽略大小写, 离线玩家按记录精确匹配.
     *
     * @return 解析任务, 找不到玩家时结果为空
     */
    @NotNull
    static CompletableFuture<Optional<PlayerRef>> resolvePlayer(@NotNull SparrowPlugin plugin, @NotNull String input) {
        UUID uuid = parseUuid(input);
        return uuid != null ? plugin.playerManager().resolvePlayer(uuid) : plugin.playerManager().resolvePlayer(input);
    }

    /**
     * 玩家的最近登录 IPv4. 在本服且使用 IPv4 连接时取当前地址, 否则取数据库记录.
     *
     * @return 查询任务, 没有记录时结果为空
     */
    @NotNull
    static CompletableFuture<Optional<IpRange>> lastIp(@NotNull SparrowPlugin plugin, @NotNull UUID player) {
        SparrowPlayer online = plugin.playerManager().getPlayer(player);
        InetSocketAddress address = online == null ? null : online.platformPlayer().getAddress();
        long current = address == null ? IpRange.NONE : IpRange.address(address.getAddress());
        if (current != IpRange.NONE) return CompletableFuture.completedFuture(Optional.of(IpRange.of(current)));
        return plugin.dataStorage().loadPlayer(player).thenApply(found -> found.map(data -> data.lastLoginIp()).map(IpRange::parse));
    }

    // 数据库或 Redis 出错时记录日志并提示执行人
    static Void failed(@NotNull BukkitCommandFeature command, @NotNull CommandSender sender, @NotNull Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        command.plugin().logger().warn("Failed to execute command " + command.getFeatureID(), cause);
        command.handleFeedback(sender, MessageConstants.COMMAND_DATABASE_FAILED);
        return null;
    }
}
