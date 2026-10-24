package net.momirealms.sparrow.plugin.command.panel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

public final class CommandPanel {
    private static final TextColor TITLE = TextColor.color(0xFFE08A);
    private static final TextColor LABEL = TextColor.color(0xAAB4BE);
    private static final TextColor VALUE = TextColor.color(0xF2F4FF);
    static final TextColor ACCENT = TextColor.color(0xC7BCE7);
    static final TextColor MUTED = TextColor.color(0x7D98BF);

    private final Component title;
    private final List<Row> rows;
    private final List<PanelButton> buttons;
    private final @Nullable Pagination pagination;

    private CommandPanel(Builder builder) {
        this.title = builder.title;
        this.rows = List.copyOf(builder.rows);
        this.buttons = List.copyOf(builder.buttons);
        this.pagination = builder.pagination;
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    public void send(@NotNull CommandSender receiver) {
        RenderContext context = new RenderContext(receiver);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("── ", MUTED)
                .append(context.text(this.title, TITLE).decorate(TextDecoration.BOLD))
                .append(Component.text(" ──", MUTED)));
        if (this.rows.isEmpty()) {
            lines.add(context.text(Component.translatable("command.panel.empty"), LABEL));
        }
        for (Row row : this.rows) {
            switch (row) {
                case Section section -> lines.add(Component.text("─ ", MUTED).append(context.text(section.title(), TITLE)));
                case Field field -> {
                    Component value = context.text(field.value(), VALUE);
                    if (field.copyValue() != null && receiver instanceof Player) {
                        value = value.clickEvent(ClickEvent.copyToClipboard(field.copyValue()))
                                .hoverEvent(context.text(Component.translatable("command.panel.copy"), LABEL)
                                        .append(Component.newline())
                                        .append(Component.text(field.copyValue(), VALUE)));
                    }
                    lines.add(context.text(field.label(), LABEL).append(Component.text(": ", LABEL)).append(value));
                }
            }
        }
        if (!this.buttons.isEmpty()) {
            lines.add(Component.join(JoinConfiguration.separator(Component.space()), this.buttons.stream().map(button -> button.render(context)).toList()));
        }
        if (this.pagination != null) {
            lines.add(this.pagination.render(context));
        }
        context.manager.handleCommandFeedback(receiver, "command.panel.message", Component.join(JoinConfiguration.newlines(), lines));
    }

    private sealed interface Row permits Field, Section {
    }

    private record Field(Component label, Component value, @Nullable String copyValue) implements Row {
    }

    private record Section(Component title) implements Row {
    }

    private record Pagination(int page, int pages, String commandId, String previous, String next, String refresh) {

        private Component render(RenderContext context) {
            Component previous = PanelButton.run(Component.translatable("command.panel.previous"), this.commandId, this.previous)
                    .disabledIf(this.page == 1, Component.translatable("command.panel.first-page"))
                    .render(context);
            Component next = PanelButton.run(Component.translatable("command.panel.next"), this.commandId, this.next)
                    .disabledIf(this.page == this.pages, Component.translatable("command.panel.last-page"))
                    .render(context);
            Component refresh = PanelButton.run(Component.translatable("command.panel.refresh"), this.commandId, this.refresh).render(context);
            return previous.append(Component.text(" " + this.page + "/" + this.pages + " ", TITLE))
                    .append(next)
                    .append(Component.space())
                    .append(refresh);
        }
    }

    static final class RenderContext {
        final CommandManager manager;
        final CommandSender receiver;
        private final TranslationManager translations;
        private final @Nullable Player player;
        private final @Nullable Locale locale;

        RenderContext(CommandSender receiver) {
            SparrowPlugin plugin = SparrowPlugin.instance();
            this.manager = plugin.commandManager();
            this.receiver = receiver;
            this.translations = plugin.translationManager();
            this.player = receiver instanceof Player current ? current : null;
            BukkitSparrowPlayer local = this.player == null ? null : plugin.playerManager().getOnlinePlayer(this.player);
            this.locale = local == null ? null : local.locale();
        }

        Component text(Component component, TextColor color) {
            return this.style(this.translations.render(component, this.locale, this.player), color, true);
        }

        Component caption(Component component, TextColor color) {
            return this.style(this.translations.render(component, this.locale, this.player), color, false);
        }

        // 模板和参数展开后应用固定配色. 字段值保留交互, 按钮文字的交互统一由按钮生成.
        private Component style(Component component, TextColor color, boolean interactive) {
            Component result = component.color(color);
            if (!interactive) {
                result = result.clickEvent(null).hoverEvent(null);
            }
            if (result instanceof TranslatableComponent translated && !translated.arguments().isEmpty()) {
                List<TranslationArgument> arguments = translated.arguments().stream()
                        .map(argument -> argument.value() instanceof Component value ? TranslationArgument.component(this.style(value, color, interactive)) : argument)
                        .toList();
                result = translated.arguments(arguments);
            }
            return result.children(result.children().stream().map(child -> this.style(child, color, interactive)).toList());
        }
    }

    public static final class Builder {
        private Component title = Component.translatable("command.panel.title");
        private final List<Row> rows = new ArrayList<>();
        private final List<PanelButton> buttons = new ArrayList<>();
        private @Nullable Pagination pagination;

        private Builder() {
        }

        @NotNull
        public Builder title(@NotNull String title) {
            return this.title(Component.text(title));
        }

        @NotNull
        public Builder title(@NotNull Component title) {
            this.title = title;
            return this;
        }

        @NotNull
        public Builder field(@NotNull String label, @NotNull String value) {
            return this.field(Component.text(label), Component.text(value));
        }

        @NotNull
        public Builder field(@NotNull String label, @NotNull Component value) {
            return this.field(Component.text(label), value);
        }

        @NotNull
        public Builder field(@NotNull Component label, @NotNull String value) {
            return this.field(label, Component.text(value));
        }

        @NotNull
        public Builder field(@NotNull Component label, @NotNull Component value) {
            this.rows.add(new Field(label, value, null));
            return this;
        }

        @NotNull
        public Builder copyField(@NotNull String label, @NotNull String value) {
            return this.copyField(Component.text(label), value);
        }

        @NotNull
        public Builder copyField(@NotNull Component label, @NotNull String value) {
            this.rows.add(new Field(label, Component.text(value), value));
            return this;
        }

        @NotNull
        public Builder section(@NotNull String title) {
            return this.section(Component.text(title));
        }

        @NotNull
        public Builder section(@NotNull Component title) {
            this.rows.add(new Section(title));
            return this;
        }

        @NotNull
        public Builder buttons(@NotNull PanelButton... buttons) {
            this.buttons.addAll(Arrays.asList(buttons));
            return this;
        }

        // page 和 pages 均从 1 开始, 调用方提供有效页码; 参数工厂在构建时求值.
        @NotNull
        public Builder pagination(int page, int pages, @NotNull String commandId, @NotNull IntFunction<String> arguments) {
            this.pagination = new Pagination(
                    page,
                    pages,
                    commandId,
                    page > 1 ? arguments.apply(page - 1) : "",
                    page < pages ? arguments.apply(page + 1) : "",
                    arguments.apply(page)
            );
            return this;
        }

        @NotNull
        public Builder pagination(@NotNull TextPage<?> page, @NotNull String commandId, @NotNull IntFunction<String> arguments) {
            return this.pagination(page.index() + 1, page.count(), commandId, arguments);
        }

        @NotNull
        public CommandPanel build() {
            return new CommandPanel(this);
        }
    }
}