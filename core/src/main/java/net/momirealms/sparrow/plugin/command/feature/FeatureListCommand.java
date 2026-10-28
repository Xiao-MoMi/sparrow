package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.feature.FeatureManager;
import net.momirealms.sparrow.feature.FeatureState;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.panel.PanelText;
import net.momirealms.sparrow.plugin.command.panel.PanelTone;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class FeatureListCommand extends BukkitCommandFeature {

    public FeatureListCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.optional("page", IntegerParser.integerParser(1))
                .handler(context -> this.createPanel(context.getOrDefault("page", 1)).send(context.sender())));
    }

    private CommandPanel createPanel(int requestedPage) {
        FeatureManager manager = this.plugin().featureManager();
        List<String> ids = List.copyOf(manager.ids());
        int total = ids.size();
        Component enable = this.tr("panel.enable");
        Component disable = this.tr("panel.disable");
        CommandPanel.Builder panel = CommandPanel.list()
                .title(this.tr("panel.title"))
                .status(PanelText.of(Component.translatable("command.panel.total", Component.text(total))).tone(PanelTone.MUTED))
                .actionColumn("enable", enable)
                .actionColumn("disable", disable);
        int pageSize = panel.getPageCapacity();
        int pages = Math.max(1, (total + pageSize - 1) / pageSize);
        int page = Math.min(requestedPage, pages);
        if (total == 0) {
            panel.entry(PanelText.of(this.tr("empty")).tone(PanelTone.MUTED));
        }
        for (int i = (page - 1) * pageSize, end = Math.min(page * pageSize, total); i < end; i++) {
            String id = ids.get(i);
            Feature feature = manager.feature(id);
            FeatureState state = manager.getState(id);
            boolean hotEnable = feature.supportsHotEnable();
            boolean hotDisable = feature.supportsHotDisable();
            boolean enabled = manager.isEnabled(id);
            String unavailable = state == FeatureState.FAILED ? "restart_required" : (manager.isBusy() ? "operation_busy" : null);
            String enableReason = unavailable != null ? unavailable : (!hotEnable ? "restart_required" : (enabled ? "already_enabled" : null));
            String disableReason = unavailable != null ? unavailable : (!hotDisable ? "restart_required" : (!enabled ? "already_disabled" : null));
            PanelTone tone = switch (state) {
                case ENABLED -> PanelTone.SUCCESS;
                case UNINSTALLED -> PanelTone.INFO;
                case FAILED -> PanelTone.DANGER;
                case DISABLED, UNLOADED -> PanelTone.MUTED;
            };
            Component status = this.tr("panel.state." + state.name().toLowerCase(Locale.ROOT));
            Component details = this.tr(
                    "panel.hover",
                    Component.text(id),
                    Component.translatable("feature.state." + state.name().toLowerCase(Locale.ROOT)),
                    this.tr(hotEnable ? "panel.hot-supported" : "panel.hot-unsupported"),
                    this.tr(hotDisable ? "panel.hot-supported" : "panel.hot-unsupported")
            );
            panel.statusEntry(
                    PanelText.of(id).copy(id).hover(details),
                    PanelText.of(status).tone(tone).hover(details),
                    this.action("enable", id, enableReason, PanelTone.SUCCESS),
                    this.action("disable", id, disableReason, PanelTone.DANGER)
            );
        }
        return panel.pagination(page, pages, this.getFeatureID(), String::valueOf).build();
    }

    private PanelButton action(String operation, String id, @Nullable String unavailable, PanelTone tone) {
        PanelButton button = PanelButton.run(this.tr("panel." + operation), "feature_" + operation, id)
                .column(operation)
                .tone(tone)
                .hint(this.tr("panel." + operation + "-hint"));
        return unavailable == null ? button : button.disabledIf(true, this.tr(unavailable));
    }

    private Component tr(String key, Component... arguments) {
        return Component.translatable("command.features." + key, arguments);
    }

    @Override
    public String getFeatureID() {
        return "feature_list";
    }
}