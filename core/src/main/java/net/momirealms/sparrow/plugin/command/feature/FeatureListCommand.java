package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.feature.FeatureManager;
import net.momirealms.sparrow.feature.FeatureState;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class FeatureListCommand extends BukkitCommandFeature {
    private static final int PAGE_SIZE = 7;

    public FeatureListCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.optional("page", IntegerParser.integerParser(1))
                .handler(context -> this.plugin().scheduler().platform().execute(() -> this.renderPage(context.sender(), context.<Integer>optional("page").orElse(1))));
    }

    private void renderPage(@NotNull CommandSender sender, int requestedPage) {
        FeatureManager manager = this.plugin().featureManager();
        List<String> ids = List.copyOf(manager.ids());
        int total = ids.size();
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(requestedPage, pages);
        Component panel = this.tr("header", Component.text(page), Component.text(pages), Component.text(total));
        int end = Math.min(page * PAGE_SIZE, total);
        for (int i = (page - 1) * PAGE_SIZE; i < end; i++) {
            String id = ids.get(i);
            Feature<?> feature = manager.feature(id);
            FeatureState state = feature.state().get();
            String unavailable = !feature.hotToggleable() || state == FeatureState.FAILED ? "restart_required"
                    : this.plugin().isReloading() ? "busy" : null;
            Component actions = this.action(sender, "enable", "feature_enable", id,
                            unavailable != null ? unavailable : feature.enabled() ? "already_enabled" : null)
                    .append(Component.space()).append(this.action(sender, "disable", "feature_disable", id,
                            unavailable != null ? unavailable : !feature.enabled() ? "already_disabled" : null))
                    .append(Component.space()).append(this.action(sender, "status", "feature_status", id, null));
            Component row = this.tr("row", Component.text(id),
                    Component.translatable("feature.state." + state.name().toLowerCase(Locale.ROOT)), actions);
            panel = panel.append(Component.newline()).append(row);
        }
        if (total == 0) {
            panel = panel.append(Component.newline()).append(this.tr("empty"));
        }
        panel = panel.append(Component.newline()).append(this.tr("navigation",
                this.action(sender, "previous", "features", Integer.toString(page - 1), page == 1 ? "first_page" : null),
                Component.text(page), Component.text(pages),
                this.action(sender, "next", "features", Integer.toString(page + 1), page == pages ? "last_page" : null),
                this.action(sender, "refresh", "features", Integer.toString(page), null)));
        this.handleFeedback(sender, Component.translatable().key("command.features.message"), panel);
    }

    @NotNull
    private Component action(@NotNull CommandSender sender, @NotNull String label, @NotNull String commandId, @NotNull String arguments, @Nullable String unavailable) {
        CommandFeature commandFeature = this.commandManager.features().value(commandId);
        CommandConfig config = commandFeature == null ? null : commandFeature.commandConfig();
        Component caption = this.tr("label." + label);
        String usage = null;
        if (config != null && config.isEnable()) {
            List<String> usages = config.getUsages();
            int size = usages.size();
            for (int i = 0; i < size; i++) {
                String candidate = usages.get(i);
                if (candidate.startsWith("/")) {
                    usage = candidate.trim();
                    break;
                }
            }
        }
        String permission = config == null ? null : config.getPermission();
        boolean permitted = permission == null || permission.isEmpty() || sender.hasPermission(permission);
        if (unavailable != null || usage == null || !permitted) {
            Component reason = this.tr(!permitted ? "no_permission" : unavailable != null ? unavailable : "unavailable");
            return sender instanceof Player ? this.tr("disabled", caption).hoverEvent(reason) : this.tr("console.disabled", caption, reason);
        }
        // 使用已注册命令的用法和权限, 自定义入口也能通过面板操作.
        String command = usage + " " + arguments;
        if (!(sender instanceof Player)) {
            return this.tr("console.action", this.tr("action." + label, caption), Component.text(command));
        }
        return this.tr("action." + label, caption)
                .hoverEvent(this.tr("hint." + label, Component.text(command)))
                .clickEvent(ClickEvent.runCommand(command));
    }

    @NotNull
    private Component tr(@NotNull String key, @NotNull Component... arguments) {
        return Component.empty().append(Component.translatable("command.features." + key).arguments(arguments));
    }

    @Override
    public String getFeatureID() {
        return "feature_list";
    }
}
