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

import java.util.List;

public final class PanelButton {
    private final CommandManager manager;
    private final CommandSender sender;
    private final Component caption;
    private final String featureId;
    private final String arguments;
    private final boolean suggest;
    private Style style = Style.NORMAL;
    private @Nullable String permission;
    private @Nullable Component disabled;
    private boolean playersOnly;

    PanelButton(
            @NotNull CommandManager manager,
            @NotNull CommandSender sender,
            @NotNull Component caption,
            @NotNull String featureId,
            @NotNull String arguments,
            boolean suggest
    ) {
        this.manager = manager;
        this.sender = sender;
        this.caption = caption;
        this.featureId = featureId;
        this.arguments = arguments;
        this.suggest = suggest;
    }

    @NotNull
    public PanelButton style(@NotNull Style style) {
        this.style = style;
        return this;
    }

    // 子命令的完整权限节点, 与目标命令的基础权限同时检查.
    @NotNull
    public PanelButton permission(@NotNull String permission) {
        this.permission = permission;
        return this;
    }

    @NotNull
    public PanelButton playersOnly() {
        this.playersOnly = true;
        return this;
    }

    // 业务条件不满足时提供原因, 传 null 表示该条件允许操作.
    @NotNull
    public PanelButton disabled(@Nullable Component reason) {
        this.disabled = reason;
        return this;
    }

    // 可用于决定是否展示按钮; build 也会按当前配置和权限重新检查.
    public boolean available() {
        CommandConfig config = this.config();
        return this.disabledReason(config, usage(config)) == null;
    }

    @NotNull
    public Component build() {
        CommandConfig config = this.config();
        String usage = usage(config);
        Component reason = this.disabledReason(config, usage);
        if (reason != null) {
            return this.sender instanceof Player
                    ? Component.translatable("command.panel.disabled", this.caption).hoverEvent(reason)
                    : Component.translatable("command.panel.console.disabled", this.caption, reason);
        }
        String command = this.arguments.isEmpty() ? usage : usage + " " + this.arguments;
        if (!(this.sender instanceof Player)) return Component.translatable("command.panel.console.action", this.caption, Component.text(command));
        Component hover = this.suggest
                ? Component.translatable("command.panel.confirm", Component.text(command))
                : Component.text(command);
        ClickEvent<ClickEvent.Payload.Text> clickEvent = this.suggest
                ? ClickEvent.suggestCommand(command)
                : ClickEvent.runCommand(command);
        return Component.translatable("command.panel.button." + this.style.key, this.caption)
                .hoverEvent(hover)
                .clickEvent(clickEvent);
    }

    @Nullable
    private CommandConfig config() {
        CommandFeature feature = this.manager.feature(this.featureId);
        return feature == null ? null : feature.commandConfig();
    }

    @Nullable
    private static String usage(@Nullable CommandConfig config) {
        if (config == null || !config.isEnable()) return null;
        List<String> usages = config.getUsages();
        int size = usages.size();
        for (int i = 0; i < size; i++) {
            String candidate = usages.get(i);
            if (candidate.startsWith("/")) return candidate.trim();
        }
        return null;
    }

    @Nullable
    private Component disabledReason(@Nullable CommandConfig config, @Nullable String usage) {
        String basePermission = config == null ? null : config.getPermission();
        if (basePermission != null && !basePermission.isEmpty() && !this.sender.hasPermission(basePermission))
            return Component.translatable("command.panel.no_permission");
        if (this.permission != null && !this.permission.isEmpty() && !this.sender.hasPermission(this.permission))
            return Component.translatable("command.panel.no_permission");
        if (this.disabled != null)
            return this.disabled;
        if (this.playersOnly && !(this.sender instanceof Player))
            return Component.translatable("command.panel.player_required");
        return usage == null ? Component.translatable("command.panel.unavailable") : null;
    }

    public enum Style {
        NORMAL("normal"),
        POSITIVE("positive"),
        DANGER("danger");

        private final String key;

        Style(String key) {
            this.key = key;
        }
    }
}
