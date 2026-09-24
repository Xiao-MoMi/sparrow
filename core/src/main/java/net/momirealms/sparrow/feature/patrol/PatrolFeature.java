package net.momirealms.sparrow.feature.patrol;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.configuration.FeaturesConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 按"最久没被巡查"的顺序轮流挑选巡查对象. 刚进服的玩家优先, 玩家退出后移出队列.
 */
public final class PatrolFeature extends Feature<PatrolSettings> implements Listener {
    public static final String ID = "patrol";
    public static final String BYPASS_PERMISSION = DependencyVersions.PROJECT_ID + ".bypass.patrol"; // 拥有此权限的玩家不会被巡查

    private final JavaPlugin plugin;
    private final FeaturesConfig featuresConfig;
    // 队首最久没被巡查. Folia 上命令与进退服事件来自不同线程, 并发时可能短暂重复或让两名巡查者选中同一玩家, 不影响后续轮换
    private final ConcurrentLinkedDeque<UUID> queue = new ConcurrentLinkedDeque<>();

    public PatrolFeature(@NotNull JavaPlugin plugin, @NotNull FeaturesConfig featuresConfig) {
        super(ID);
        this.plugin = plugin;
        this.featuresConfig = featuresConfig;
    }

    @Override
    public void loadConfig() {
        this.config = this.featuresConfig.config().patrol();
    }

    // 队列从安装起持续维护, 停用只由命令入口拒绝新巡查
    @Override
    protected void onLoad() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            this.queue.addLast(player.getUniqueId());
        }
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        this.queue.addFirst(event.getPlayer().getUniqueId());
    }

    // 并发移动可能留下重复记录, 退出时全部移除
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        this.queue.removeIf(id::equals);
    }

    /**
     * 从候选玩家中取出队列里最靠前的一位, 并把他移到队尾.
     * 巡查者自己、拥有绕过权限的玩家以及不符合模块筛选规则的玩家会被跳过.
     *
     * @param patroller 执行巡查的玩家
     * @param candidates 允许被选中的玩家
     * @return 下一位巡查对象, 没有符合条件的玩家时为 null
     */
    @Nullable
    public Player claimNext(@NotNull Player patroller, @NotNull Collection<? extends Player> candidates) {
        PatrolSettings config = this.config();
        Map<UUID, Player> eligible = new HashMap<>();
        for (Player candidate : candidates) {
            if (this.patrollable(patroller, candidate, config)) {
                eligible.put(candidate.getUniqueId(), candidate);
            }
        }
        if (eligible.isEmpty()) return null;
        for (UUID id : this.queue) {
            Player next = eligible.get(id);
            if (next == null) continue;
            // 移到队尾. 已被其他线程移走时仍选中该玩家
            if (this.queue.removeFirstOccurrence(id)) {
                this.queue.addLast(id);
            }
            return next;
        }
        return null;
    }

    private boolean patrollable(Player patroller, Player candidate, PatrolSettings config) {
        if (candidate.getUniqueId().equals(patroller.getUniqueId()) || !candidate.isOnline()) return false;
        if (candidate.hasPermission(BYPASS_PERMISSION)) return false;
        if (config.skipSpectators() && candidate.getGameMode() == GameMode.SPECTATOR) return false;
        return !config.excludedWorlds().contains(candidate.getWorld().getName());
    }
}
