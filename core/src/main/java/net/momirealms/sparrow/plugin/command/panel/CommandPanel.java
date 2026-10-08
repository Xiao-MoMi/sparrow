package net.momirealms.sparrow.plugin.command.panel;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntFunction;

/**
 * 为一个接收者组装聊天栏面板. 每次展示创建一个实例, 按行添加内容, 最后发送或取出组件.
 * 数据查询、行内字段和操作确认由具体命令负责.
 */
public final class CommandPanel {
    public static final String MESSAGE_KEY = "command.panel.message";

    private final CommandManager manager;
    private final CommandSender sender;
    private Component content = Component.empty();
    private boolean hasLines;

    public CommandPanel(@NotNull CommandManager manager, @NotNull CommandSender sender) {
        this.manager = manager;
        this.sender = sender;
    }

    @NotNull
    public CommandPanel header(@NotNull Component title, @NotNull TextPage<?> page) {
        return this.line(Component.translatable(
                "command.panel.header",
                title,
                Component.text(page.index() + 1),
                Component.text(page.count()),
                Component.text(page.total())
        ));
    }

    // 同一行的片段按原样连接, 行之间自动换行.
    @NotNull
    public CommandPanel line(@NotNull Component... parts) {
        if (this.hasLines) {
            this.content = this.content.append(Component.newline());
        }
        for (int i = 0; i < parts.length; i++) {
            this.content = this.content.append(parts[i]);
        }
        this.hasLines = true;
        return this;
    }

    @NotNull
    public CommandPanel empty() {
        return this.line(Component.translatable("command.panel.empty"));
    }

    // 操作单独占一行, 按钮之间加空格.
    @NotNull
    public CommandPanel actions(@NotNull Component... buttons) {
        Component row = Component.empty();
        for (int i = 0; i < buttons.length; i++) {
            if (i > 0) {
                row = row.append(Component.space());
            }
            row = row.append(buttons[i]);
        }
        return this.line(row);
    }

    @NotNull
    public PanelButton run(@NotNull Component caption, @NotNull String featureId, @NotNull String arguments) {
        return new PanelButton(this.manager, this.sender, caption, featureId, arguments, false);
    }

    @NotNull
    public PanelButton suggest(@NotNull Component caption, @NotNull String featureId, @NotNull String arguments) {
        return new PanelButton(this.manager, this.sender, caption, featureId, arguments, true);
    }

    /**
     * 添加上一页、页码、下一页和刷新按钮. 翻页参数由具体命令提供, 可带回对象和筛选条件.
     *
     * @param pageArguments 输入从 1 开始的页码, 返回对应页的完整命令参数
     */
    @NotNull
    public CommandPanel navigation(@NotNull String featureId, @NotNull TextPage<?> page, @NotNull IntFunction<String> pageArguments) {
        return this.navigation(featureId, page, pageArguments, false);
    }

    @NotNull
    public CommandPanel navigation(@NotNull String featureId, @NotNull TextPage<
            ?> page, @NotNull IntFunction<String> pageArguments, boolean suggest) {
        Component previous = new PanelButton(
                this.manager,
                this.sender,
                label("previous"),
                featureId,
                page.hasPrevious() ? pageArguments.apply(page.index()) : "",
                suggest
        )
                .style(PanelButton.Style.POSITIVE)
                .disabled(page.hasPrevious() ? null : Component.translatable("command.panel.first_page"))
                .build();
        Component next = new PanelButton(
                this.manager,
                this.sender,
                label("next"),
                featureId,
                page.hasNext() ? pageArguments.apply(page.index() + 2) : "",
                suggest
        )
                .style(PanelButton.Style.POSITIVE)
                .disabled(page.hasNext() ? null : Component.translatable("command.panel.last_page"))
                .build();
        Component refresh = new PanelButton(this.manager, this.sender, label("refresh"), featureId, pageArguments.apply(page.index()
                + 1), suggest).build();
        return this.line(
                Component.translatable(
                        "command.panel.navigation",
                        previous,
                        Component.text(page.index() + 1),
                        Component.text(page.count()),
                        next,
                        refresh
                )
        );
    }

    @NotNull
    public Component build() {
        return this.content;
    }

    public void send() {
        this.manager.handleCommandFeedback(this.sender, Component.translatable(MESSAGE_KEY), this.content);
    }

    // 共用按钮文字. 功能自己的文字直接作为 Component 传给 run 或 suggest.
    @NotNull
    public static Component label(@NotNull String key) {
        return Component.translatable("command.panel.label." + key);
    }
}
