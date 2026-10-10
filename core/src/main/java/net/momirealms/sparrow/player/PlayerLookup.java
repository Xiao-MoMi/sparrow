package net.momirealms.sparrow.player;

import net.momirealms.sparrow.cluster.PlayerPresence;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerLookup {
    private final SparrowPlugin plugin = SparrowPlugin.instance();

    /**
     * 按名字解析玩家, 离线玩家也能查到.
     * 在线时名字忽略大小写, 离线时按数据库记录精确匹配.
     *
     * @param name 玩家名
     * @return 解析任务, 找不到玩家时结果为空, 数据库出错时异常完成
     */
    @NotNull
    public CompletableFuture<Optional<SparrowPlayer>> resolvePlayer(@NotNull String name) {
        Player local = Bukkit.getPlayerExact(name);
        BukkitSparrowPlayer current = local == null ? null : this.plugin.playerManager().getPlayer(local);
        if (current != null) return CompletableFuture.completedFuture(Optional.of(current));
        PlayerPresence online = this.plugin.playerDirectory().find(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(this.plugin.playerManager().getOrCreate(online.uuid(), online.name())));
        }
        return this.plugin.dataStorage().lookupUser(name).thenApply(found -> found.map(uuid -> this.plugin.playerManager().getOrCreate(uuid, name)));
    }

    /**
     * 按 UUID 解析玩家, 离线时取数据库中最近使用的名字.
     *
     * @param uniqueId 玩家 UUID
     * @return 解析任务, 找不到玩家时结果为空, 数据库出错时异常完成
     */
    @NotNull
    public CompletableFuture<Optional<SparrowPlayer>> resolvePlayer(@NotNull UUID uniqueId) {
        BukkitSparrowPlayer current = this.plugin.playerManager().getPlayer(uniqueId);
        if (current != null) return CompletableFuture.completedFuture(Optional.of(current));
        PlayerPresence online = this.plugin.playerDirectory().find(uniqueId);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(this.plugin.playerManager().getOrCreate(online.uuid(), online.name())));
        }
        return this.plugin.dataStorage().lookupName(uniqueId).thenApply(found -> found.map(name -> this.plugin.playerManager().getOrCreate(uniqueId, name)));
    }
}