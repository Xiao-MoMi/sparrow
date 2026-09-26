package net.momirealms.sparrow.feature.playerlimit;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.util.VersionHelper;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 动态调整服务器人数上限, 并允许拥有绕过权限的玩家在满员时进入.
 * 上限保存在 features.yml, 不会改写 server.properties.
 */
public final class PlayerLimitFeature extends Feature<PlayerLimitSettings> {
    public static final String ID = "player-limit";
    public static final String BYPASS_PERMISSION = DependencyVersions.PROJECT_ID + ".bypass.player-limit";

    private final SparrowPlugin plugin;
    private int defaultMaxPlayers; // 安装时生效的上限, 即 server.properties 的值

    public PlayerLimitFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        PlayerLimitSettings settings = this.plugin.configurationManager().featuresConfig().config().playerLimit();
        settings.validate();
        this.config = settings;
    }

    // Paper 1.21.7 起监听 PlayerLoginEvent 会禁用重新配置 API, 因此 Paper 上使用满员检查事件
    @Override
    protected void onLoad() {
        this.defaultMaxPlayers = Bukkit.getMaxPlayers();
        Listener listener = VersionHelper.hasPaperPatch ? new PaperPlayerLimitListener(this) : new SpigotPlayerLimitListener(this);
        Bukkit.getPluginManager().registerEvents(listener, this.plugin.javaPlugin());
    }

    @Override
    protected void onEnable() {
        this.apply();
    }

    @Override
    protected void onDisable() {
        Bukkit.setMaxPlayers(this.defaultMaxPlayers);
    }

    public void maxPlayers(int maxPlayers) {
        this.plugin.configurationManager().featuresConfig().saveMaxPlayers(maxPlayers);
        this.apply();
    }

    public int maxPlayers() {
        return Bukkit.getMaxPlayers();
    }

    public int defaultMaxPlayers() {
        return this.defaultMaxPlayers;
    }

    // 登录阶段尚无玩家实体, 通过权限插件的缓存判断
    boolean bypassBeforeJoin(@NotNull UUID uniqueId) {
        return this.plugin.compatibilityManager().hasPermissionBeforeJoin(uniqueId, BYPASS_PERMISSION);
    }

    private void apply() {
        int configured = this.config.maxPlayers();
        Bukkit.setMaxPlayers(configured < 0 ? this.defaultMaxPlayers : configured);
    }
}
