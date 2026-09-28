package net.momirealms.sparrow.plugin.command.panel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.IntFunction;

// 聊天栏文本面板的公共片段, 翻译键位于 command.panel 下.
public final class CommandPanel {
    public static final String MESSAGE_KEY = "command.panel.message";
    private static final DateTimeFormatter FULL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm").withZone(ZoneId.systemDefault());

    private CommandPanel() {
    }

    // 行内使用的短时间, 按服务器时区
    @NotNull
    public static String shortTime(long millis) {
        return SHORT_TIME.format(Instant.ofEpochMilli(millis));
    }

    // 悬浮与控制台使用的完整时间, 带时区偏移
    @NotNull
    public static String fullTime(long millis) {
        return FULL_TIME.format(Instant.ofEpochMilli(millis));
    }

    // 以面板消息发送, 与普通反馈走同一套渲染
    public static void send(@NotNull CommandManager manager, @NotNull CommandSender sender, @NotNull Component panel) {
        manager.handleCommandFeedback(sender, Component.translatable().key(MESSAGE_KEY), panel);
    }

    // 翻译节点只承载模板参数, 后续行与按钮挂在外层, 随模板替换后仍保留
    @NotNull
    public static Component tr(@NotNull String key, @NotNull Component... arguments) {
        return Component.empty().append(Component.translatable("command.panel." + key).arguments(arguments));
    }

    /**
     * 根据命令当前配置和发送者权限构建操作按钮. 入口与权限来自当前注册的命令配置, 自定义别名和权限会同步体现在链接中.
     *
     * @param sender 按钮的接收者
     * @param label 操作名称, 对应 command.panel.label.* 与 command.panel.action.*
     * @param featureId 目标命令的功能标识
     * @param arguments 附加在命令入口后的完整参数
     * @param suggest 是否只填入聊天输入框, 等待玩家确认发送
     * @param unavailable 按钮不可用时的原因翻译键 (command.panel.*), 可用时为 null
     * @return 玩家收到可点击按钮, 控制台收到完整命令文本, 不可用或无权限时显示禁用状态
     */
    @NotNull
    public static Component action(@NotNull CommandManager manager, @NotNull CommandSender sender, @NotNull String label, @NotNull String featureId,
                                   @NotNull String arguments, boolean suggest, @Nullable String unavailable) {
        CommandFeature feature = manager.features().value(featureId);
        CommandConfig config = feature == null ? null : feature.commandConfig();
        Component caption = tr("label." + label);
        String usage = config != null && config.isEnable() ? firstUsage(config.getUsages()) : null;
        // 每次渲染按发送者的当前权限生成链接, 命令执行时仍由命令框架检查权限
        String permission = config == null ? null : config.getPermission();
        boolean permitted = permission == null || permission.isEmpty() || sender.hasPermission(permission);
        if (unavailable != null || usage == null || !permitted) {
            Component reason = tr(!permitted ? "no_permission" : unavailable != null ? unavailable : "unavailable");
            return sender instanceof Player ? tr("disabled", caption).hoverEvent(reason) : tr("console.disabled", caption, reason);
        }
        String command = arguments.isEmpty() ? usage : usage + " " + arguments;
        if (!(sender instanceof Player)) return tr("console.action", caption, Component.text(command));
        Component hover = suggest ? tr("confirm", Component.text(command)) : Component.text(command);
        return tr("action." + label, caption).hoverEvent(hover).clickEvent(suggest ? ClickEvent.suggestCommand(command) : ClickEvent.runCommand(command));
    }

    /**
     * 上一页, 页码, 下一页, 刷新. 页码参数由 pageArguments 拼进命令, 页码从 1 开始.
     *
     * @param pageArguments 输入 1 起的页码, 返回对应页的完整命令参数
     */
    @NotNull
    public static Component navigation(@NotNull CommandManager manager, @NotNull CommandSender sender, @NotNull String featureId,
                                       @NotNull TextPage<?> page, @NotNull IntFunction<String> pageArguments) {
        return tr("navigation",
                action(manager, sender, "previous", featureId, pageArguments.apply(page.index()), false, page.hasPrevious() ? null : "first_page"),
                Component.text(page.index() + 1), Component.text(page.count()),
                action(manager, sender, "next", featureId, pageArguments.apply(page.index() + 2), false, page.hasNext() ? null : "last_page"),
                action(manager, sender, "refresh", featureId, pageArguments.apply(page.index() + 1), false, null));
    }

    @Nullable
    private static String firstUsage(List<String> usages) {
        int size = usages.size();
        for (int i = 0; i < size; i++) {
            String candidate = usages.get(i);
            if (candidate.startsWith("/")) return candidate.trim();
        }
        return null;
    }
}
