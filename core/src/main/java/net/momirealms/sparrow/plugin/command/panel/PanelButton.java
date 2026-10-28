package net.momirealms.sparrow.plugin.command.panel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PanelButton {
    private final Component caption;
    private final String commandId;
    private final String arguments;
    private final boolean suggest;
    private final @Nullable Component disabledReason;
    private final @Nullable String column;
    private final PanelTone tone;
    private final @Nullable Component hint;

    private PanelButton(
            Component caption,
            String commandId,
            String arguments,
            boolean suggest,
            @Nullable Component disabledReason,
            @Nullable String column,
            PanelTone tone,
            @Nullable Component hint
    ) {
        this.caption = caption;
        this.commandId = commandId;
        this.arguments = arguments;
        this.suggest = suggest;
        this.disabledReason = disabledReason;
        this.column = column;
        this.tone = tone;
        this.hint = hint;
    }

    @NotNull
    public static PanelButton run(@NotNull String caption, @NotNull String commandId, @NotNull String arguments) {
        return run(Component.text(caption), commandId, arguments);
    }

    @NotNull
    public static PanelButton run(@NotNull Component caption, @NotNull String commandId, @NotNull String arguments) {
        return new PanelButton(caption, commandId, arguments, false, null, null, PanelTone.ACTION, null);
    }

    @NotNull
    public static PanelButton suggest(@NotNull String caption, @NotNull String commandId, @NotNull String arguments) {
        return suggest(Component.text(caption), commandId, arguments);
    }

    @NotNull
    public static PanelButton suggest(@NotNull Component caption, @NotNull String commandId, @NotNull String arguments) {
        return new PanelButton(caption, commandId, arguments, true, null, null, PanelTone.ACTION, null);
    }

    @NotNull
    public PanelButton disabledIf(boolean disabled, @NotNull String reason) {
        return this.disabledIf(disabled, Component.text(reason));
    }

    @NotNull
    public PanelButton disabledIf(boolean disabled, @NotNull Component reason) {
        return disabled ? new PanelButton(this.caption, this.commandId, this.arguments, this.suggest, reason, this.column, this.tone, this.hint) : this;
    }

    @NotNull
    public PanelButton column(@NotNull String column) {
        return new PanelButton(this.caption, this.commandId, this.arguments, this.suggest, this.disabledReason, column, this.tone, this.hint);
    }

    @NotNull
    public PanelButton tone(@NotNull PanelTone tone) {
        return new PanelButton(this.caption, this.commandId, this.arguments, this.suggest, this.disabledReason, this.column, tone, this.hint);
    }

    @NotNull
    public PanelButton hint(@NotNull Component hint) {
        return new PanelButton(this.caption, this.commandId, this.arguments, this.suggest, this.disabledReason, this.column, this.tone, hint);
    }

    @Nullable
    String column() {
        return this.column;
    }

    Component render(PanelContext context) {
        CommandFeature command = context.manager.feature(this.commandId);
        if (command == null) {
            return this.renderDisabled(context, Component.translatable("command.panel.unavailable"));
        }
        CommandConfig config = command.commandConfig();
        String permission = config.getPermission();
        if (permission != null && !permission.isEmpty() && !context.receiver.hasPermission(permission)) {
            return this.renderDisabled(context, Component.translatable("command.panel.no-permission"));
        }
        if (this.disabledReason != null) {
            return this.renderDisabled(context, this.disabledReason);
        }
        if (!config.isEnable()) {
            return this.renderDisabled(context, Component.translatable("command.panel.unavailable"));
        }
        for (String usage : config.getUsages()) {
            if (usage.startsWith("/")) {
                String input = this.arguments.isEmpty() ? usage.trim() : usage.trim() + " " + this.arguments;
                return this.renderEnabled(context, input);
            }
        }
        return this.renderDisabled(context, Component.translatable("command.panel.unavailable"));
    }

    private Component renderEnabled(PanelContext context, String command) {
        if (context.interactive && this.suggest && command.length() > 256) {
            return this.renderDisabled(context, Component.translatable("command.panel.input-too-long"));
        }
        Component caption = this.caption(context, this.tone);
        if (!context.interactive) {
            return caption.append(Component.text(" " + command, PanelTone.MUTED.color()));
        }
        Component hint = this.hint == null ? Component.translatable(this.suggest ? "command.panel.suggest" : "command.panel.run") : this.hint;
        Component hover = context.text(hint, PanelTone.MUTED)
                .append(Component.newline())
                .append(Component.text(command, this.tone.color()));
        return caption.hoverEvent(hover).clickEvent(this.suggest ? ClickEvent.suggestCommand(command) : ClickEvent.runCommand(command));
    }

    private Component renderDisabled(PanelContext context, Component reason) {
        Component caption = this.caption(context, PanelTone.MUTED);
        Component explanation = context.text(reason, PanelTone.MUTED);
        return context.interactive
                ? caption.hoverEvent(explanation)
                : caption.append(Component.text(" (", PanelTone.MUTED.color())).append(explanation).append(Component.text(")", PanelTone.MUTED.color()));
    }

    private Component caption(PanelContext context, PanelTone tone) {
        return Component.text("[", tone.color())
                .append(context.caption(this.caption, tone))
                .append(Component.text("]", tone.color()));
    }
}