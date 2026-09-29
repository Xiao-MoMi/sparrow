package net.momirealms.sparrow.feature.patrol;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

/**
 * 按"最久没被巡查"的顺序轮流挑选巡查对象. 刚进服的玩家优先, 玩家退出后移出队列.
 */
public final class PatrolFeature extends Feature<PatrolSettings> implements PlayerListener {
    public static final String ID = "patrol";
    public static final String BYPASS_PERMISSION = DependencyVersions.PROJECT_ID + ".bypass.patrol"; // 拥有此权限的玩家不会被巡查

    private final SparrowPlugin plugin;
    // 队首最久没被巡查. Folia 上命令与进退服事件来自不同线程, 并发时可能短暂重复或让两名巡查者选中同一玩家, 不影响后续轮换
    private final ConcurrentLinkedDeque<UUID> queue = new ConcurrentLinkedDeque<>();

    public PatrolFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        this.config = this.plugin.configurationManager().featuresConfig().config().patrol();
    }

    // 队列从安装起持续维护, 停用只由命令入口拒绝新巡查
    @Override
    protected void onLoad() {
        for (Player player : this.plugin.javaPlugin().getServer().getOnlinePlayers()) {
            this.queue.addLast(player.getUniqueId());
        }
        this.plugin.playerManager().registerListener(this);
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        register.accept(new PatrolCommand(this.plugin.commandManager(), this.plugin));
    }

    @Override
    protected void onUnload() {
        this.plugin.playerManager().unregisterListener(this);
    }

    @Override
    public void onJoin(@NotNull SparrowPlayer player) {
        this.queue.addFirst(player.uniqueId());
    }

    // 并发移动可能留下重复记录, 退出时全部移除
    @Override
    public void onQuit(@NotNull SparrowPlayer player) {
        UUID id = player.uniqueId();
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
