package net.momirealms.sparrow.feature.maintenance;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 维护模式. 在登录阶段拒绝没有绕过权限的玩家, 使其无法进入配置阶段.
 * 登录阶段只能通过 LuckPerms 查询权限, 未接入时仅放行 OP.
 */
public final class MaintenanceFeature extends Feature<MaintenanceSettings> implements PlayerListener {
    public static final String ID = "maintenance";
    public static final String BYPASS_PERMISSION = DependencyVersions.PROJECT_ID + ".bypass.maintenance";

    private final SparrowPlugin plugin;
    private final UUID bossBarId = UUID.randomUUID();
    private final ConcurrentChainedObject2ObjectHashTable<UUID, Boolean> bossBarViewers = new ConcurrentChainedObject2ObjectHashTable<>();
    private volatile boolean active; // 登录线程异步读取

    public MaintenanceFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        MaintenanceSettings settings = this.plugin.configurationManager().featuresConfig().config().maintenance();
        settings.validate();
        super.config = settings;
    }

    @Override
    protected void onLoad() {
        this.plugin.playerManager().registerListener(this);
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        register.accept(new MaintenanceCommand(this.plugin.commandManager(), this.plugin));
    }

    @Override
    protected void onEnable() {
        this.active = super.config.active();
        if (this.active) {
            this.applyToOnlinePlayers();
        }
    }

    // 停用模块时撤下 BossBar, 维护状态仍保留在配置中.
    // 关服时插件已停用, Folia 不再接受新任务, BossBar 随玩家断开一并消失.
    @Override
    protected void onDisable() {
        this.active = false;
        if (this.plugin.javaPlugin().isEnabled()) {
            this.applyToOnlinePlayers();
        }
    }

    @Override
    protected void onUnload() {
        this.plugin.playerManager().unregisterListener(this);
    }

    // LuckPerms 在 LOW 优先级加载用户数据, 在它之后可以正常判断权限.
    @Override
    @SuppressWarnings("deprecation")
    public void onPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        if (!this.active || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        if (this.plugin.compatibilityManager().hasPermissionBeforeJoin(event.getUniqueId(), BYPASS_PERMISSION)) {
            return;
        }
        event.disallow(
                AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                AdventureHelper.componentToLegacy(this.plugin.translationManager().render(MessageConstants.MAINTENANCE_KICK, null))
        );
    }

    // 登录检查之后才开启维护的玩家在这里补查
    @Override
    public void onJoin(@NotNull SparrowPlayer player) {
        if (this.active) {
            this.apply(player);
        }
    }

    @Override
    public void onQuit(@NotNull SparrowPlayer player) {
        this.bossBarViewers.remove(player.uniqueId());
    }

    /**
     * 切换并保存维护状态. 开启时踢出没有绕过权限的在线玩家, 并向其余玩家显示 BossBar.
     *
     * @param active 是否开启维护
     */
    public void active(boolean active) {
        this.plugin.configurationManager().featuresConfig().saveMaintenanceActive(active);
        this.active = active;
        this.applyToOnlinePlayers();
    }

    public boolean active() {
        return this.active;
    }

    // 在各玩家所属线程上更新
    private void applyToOnlinePlayers() {
        for (SparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
            this.plugin.scheduler()
                    .platform()
                    .run(() -> this.apply(player), () -> {}, player.nmsPlayer().getBukkitEntity());
        }
    }

    // 更新玩家的维护状态
    private void apply(SparrowPlayer player) {
        // 未开启
        if (!this.active) {
            if (this.bossBarViewers.remove(player.uniqueId()) != null) {
                player.hideBossBar(this.bossBarId);
            }
        }
        // 有绕过权限
        else if (player.hasPermission(BYPASS_PERMISSION)) {
            this.showBossBar(player);
        }
        // 无权限, 踢出.
        else {
            player.kickFromServer(player.render(MessageConstants.MAINTENANCE_KICK));
        }
    }

    // 标题按玩家语言渲染
    private void showBossBar(SparrowPlayer player) {
        MaintenanceSettings.BossBarOptions options = super.config.bossBar();
        if (!options.enabled() || this.bossBarViewers.putIfAbsent(player.uniqueId(), Boolean.TRUE) != null) {
            return;
        }
        Component title = player.render(MessageConstants.MAINTENANCE_BOSS_BAR);
        player.showBossBar(this.bossBarId, title, 1.0f, options.color(), options.overlay());
    }
}