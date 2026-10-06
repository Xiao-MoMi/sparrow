package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.ban.BanQuery;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanTarget;
import net.momirealms.sparrow.util.IpRange;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BanStore {

    @NotNull
    CompletableFuture<Void> initialize();

    /**
     * 查询登录时生效的封禁, 同时匹配玩家 UUID 和包含该 IP 的 IP 段.
     * 优先返回封禁该玩家账号的记录, 其次是永久封禁和到期最晚的封禁.
     *
     * @param player 玩家 UUID
     * @param ip 登录 IP, 见 {@link IpRange#address}; 为 {@link IpRange#NONE} 时只匹配 UUID
     * @param now 当前时间, 单位为 Unix 毫秒
     * @return 查询任务, 没有生效的封禁时结果为空
     */
    @NotNull
    CompletableFuture<Optional<BanRecord>> findActiveBan(@NotNull UUID player, long ip, long now);

    /**
     * 写入封禁, 并撤销同一对象上仍生效的旧封禁.
     * 带玩家的记录按 UUID 判定同一对象, 纯 IP 记录按 IP 段完全相同判定.
     *
     * @param banRecord 新的封禁记录
     * @return 写入任务, 结果表示是否覆盖了旧封禁
     */
    @NotNull
    CompletableFuture<Boolean> saveBan(@NotNull BanRecord banRecord);

    /**
     * 撤销仍生效的封禁.
     * 玩家匹配 UUID (包含账号加 IP 的封禁), IP 只匹配段完全相同的纯 IP 封禁, ID 精确匹配.
     *
     * @return 撤销任务, 结果为被撤销的记录 (撤销前的状态)
     */
    @NotNull
    CompletableFuture<List<BanRecord>> revokeBans(@NotNull BanTarget target, long now, @NotNull String revokedBy);

    /**
     * 计算符合条件的封禁记录数量.
     *
     * @param query 搜索条件
     * @return 封禁条目数量
     */
    @NotNull
    CompletableFuture<Long> countBans(@NotNull BanQuery query);

    /**
     * 查询封禁数据, 按封禁时间倒序读取一页.
     *
     * @param query 搜索条件
     * @param offset 起始偏移
     * @param limit 查询长度
     * @return 封禁条目
     */
    @NotNull
    CompletableFuture<List<BanRecord>> listBans(@NotNull BanQuery query, int offset, int limit);
}
