package net.momirealms.sparrow.feature.warp;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.feature.warp.command.*;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class WarpFeature extends Feature<WarpSettings> {
    public static final String ID = "warp";
    public static final String PERMISSION_PREFIX = DependencyVersions.PROJECT_ID + ".warp."; // 开启权限限制后, 加上小写名称即为该 warp 的权限

    private final SparrowPlugin plugin;
    private WarpRegistry registry;
    private WarpService service;

    public WarpFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    // 配置写错时在启用前报告
    @Override
    public void loadConfig() {
        WarpSettings settings = this.plugin.configurationManager().featuresConfig().config().warp();
        if (settings.suggestionLimit() < 1) throw new IllegalArgumentException("warp.suggestion-limit must be at least 1");
        try {
            Pattern.compile(settings.namePattern());
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException("warp.name-pattern is not a valid regular expression: " + settings.namePattern(), exception);
        }
        this.config = settings;
    }

    @Override
    protected void onLoad() {
        this.registry = new WarpRegistry();
        this.service = new WarpService();
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        CommandManager manager = this.plugin.commandManager();
        register.accept(new WarpCommand(manager, this.plugin, this));
        register.accept(new SetWarpCommand(manager, this.plugin, this));
        register.accept(new DelWarpCommand(manager, this.plugin, this));
        register.accept(new WarpListCommand(manager, this.plugin, this));
        register.accept(new EditWarpCommand(manager, this.plugin, this));
    }

    // 停用期间收不到其他服务器的变更, 每次启用都整表重读. 读完之前阻塞, 启用后的查找一定能命中
    @Override
    protected void onEnable() {
        this.registry.load();
        WarpMessage.listener(this.registry::accept);
    }

    @Override
    protected void onDisable() {
        WarpMessage.listener(null);
    }

    @NotNull
    public WarpRegistry registry() {
        return this.registry;
    }

    @NotNull
    public WarpService service() {
        return this.service;
    }

    /**
     * 开启权限限制时, 只有拥有对应权限的执行者能看到和使用该 warp.
     */
    public boolean visible(@NotNull CommandSender sender, @NotNull Warp warp) {
        return !this.config.permissionRestrict() || sender.hasPermission(PERMISSION_PREFIX + warp.key());
    }

    // 补全只读内存, 按前缀最多返回 suggestion-limit 个
    @NotNull
    public List<String> suggest(@NotNull CommandSender sender, @NotNull String input) {
        return this.registry.complete(input, warp -> this.visible(sender, warp), this.config.suggestionLimit());
    }
}
