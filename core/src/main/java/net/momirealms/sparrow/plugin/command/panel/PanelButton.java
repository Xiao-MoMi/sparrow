package net.momirealms.sparrow.plugin.command.panel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PanelButton {
    private final Component caption;
    private final String commandId;
    private final String arguments;
    private final boolean suggest;
    private final @Nullable Component disabledReason;

    private PanelButton(Component caption, String commandId, String arguments, boolean suggest, @Nullable Component disabledReason) {
        this.caption = caption;
        this.commandId = commandId;
        this.arguments = arguments;
        this.suggest = suggest;
        this.disabledReason = disabledReason;
    }

    @NotNull
    public static PanelButton run(@NotNull String caption, @NotNull String commandId, @NotNull String arguments) {
        return run(Component.text(caption), commandId, arguments);
    }

    @NotNull
    public static PanelButton run(@NotNull Component caption, @NotNull String commandId, @NotNull String arguments) {
        return new PanelButton(caption, commandId, arguments, false, null);
    }

    @NotNull
    public static PanelButton suggest(@NotNull String caption, @NotNull String commandId, @NotNull String arguments) {
        return suggest(Component.text(caption), commandId, arguments);
    }

    @NotNull
    public static PanelButton suggest(@NotNull Component caption, @NotNull String commandId, @NotNull String arguments) {
        return new PanelButton(caption, commandId, arguments, true, null);
    }

    @NotNull
    public PanelButton disabledIf(boolean disabled, @NotNull String reason) {
        return this.disabledIf(disabled, Component.text(reason));
    }

    @NotNull
    public PanelButton disabledIf(boolean disabled, @NotNull Component reason) {
        return disabled ? new PanelButton(this.caption, this.commandId, this.arguments, this.suggest, reason) : this;
    }

    Component render(CommandPanel.RenderContext context) {
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
        for (String usage : config.getUsages()) {
            if (usage.startsWith("/")) {
                String input = this.arguments.isEmpty() ? usage.trim() : usage.trim() + " " + this.arguments;
                return this.renderEnabled(context, input);
            }
        }
        return this.renderDisabled(context, Component.translatable("command.panel.unavailable"));
    }

    private Component renderEnabled(CommandPanel.RenderContext context, String command) {
        Component caption = Component.text("[", CommandPanel.ACCENT)
                .append(context.caption(this.caption, CommandPanel.ACCENT))
                .append(Component.text("]", CommandPanel.ACCENT));
        if (!(context.receiver instanceof Player)) {
            return caption.append(Component.text(" " + command, CommandPanel.MUTED));
        }
        Component hover = context.text(Component.translatable(this.suggest ? "command.panel.suggest" : "command.panel.run"), CommandPanel.MUTED)
                .append(Component.newline())
                .append(Component.text(command, CommandPanel.ACCENT));
        return caption.hoverEvent(hover).clickEvent(this.suggest ? ClickEvent.suggestCommand(command) : ClickEvent.runCommand(command));
    }

    private Component renderDisabled(CommandPanel.RenderContext context, Component reason) {
        Component caption = Component.text("[", CommandPanel.MUTED)
                .append(context.caption(this.caption, CommandPanel.MUTED))
                .append(Component.text("]", CommandPanel.MUTED));
        Component explanation = context.text(reason, CommandPanel.MUTED);
        return context.receiver instanceof Player
                ? caption.hoverEvent(explanation)
                : caption.append(Component.text(" (", CommandPanel.MUTED)).append(explanation).append(Component.text(")", CommandPanel.MUTED));
    }
}