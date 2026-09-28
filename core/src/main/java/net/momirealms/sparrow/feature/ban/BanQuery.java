package net.momirealms.sparrow.feature.ban;

import org.jetbrains.annotations.Nullable;

/**
 * 封禁记录的筛选条件, 结果按封禁时间倒序. 各条件同时生效.
 *
 * @param target 只看影响该对象的记录, 为 null 时不限. 玩家匹配 UUID, IP 匹配完整覆盖它的 IP 段, ID 精确匹配
 * @param operator 只看该执行人的记录, 忽略大小写, 为 null 时不限
 * @param since 只看这个时间及之后的封禁, 单位为 Unix 毫秒, 0 表示不限
 * @param activeOnly 只看仍生效的记录
 * @param now 判断是否生效时使用的当前时间, 单位为 Unix 毫秒
 */
public record BanQuery(@Nullable BanTarget target, @Nullable String operator, long since, boolean activeOnly, long now) {
}
