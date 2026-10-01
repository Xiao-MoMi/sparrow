package net.momirealms.sparrow.feature.warp;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.TextPage;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class WarpListCommand extends BukkitCommandFeature {
    private static final int PAGE_SIZE = 10;

    private final WarpFeature feature;

    public WarpListCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull WarpFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.optional("page", IntegerParser.integerParser(1))
                .handler(this::execute));
    }

    // 列表只读内存, 权限限制下只列出执行者能用的 warp
    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        List<Warp> all = this.feature.registry().all();
        List<Warp> visible = new ArrayList<>();
        int size = all.size();
        for (int i = 0; i < size; i++) {
            Warp warp = all.get(i);
            if (this.feature.visible(sender, warp)) visible.add(warp);
        }
        int pages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int index = Math.min(context.<Integer>getOrDefault("page", 1), pages) - 1;
        int from = index * PAGE_SIZE;
        TextPage<Warp> page = new TextPage<>(index, PAGE_SIZE, visible.size(), visible.subList(from, Math.min(from + PAGE_SIZE, visible.size())));
        Component panel = CommandPanel.tr("header", Component.translatable("command.warp-list.title"), Component.text(page.index() + 1), Component.text(page.count()), Component.text(page.total()));
        if (page.content().isEmpty()) {
            panel = panel.append(Component.newline()).append(CommandPanel.tr("empty"));
        }
        List<Warp> content = page.content();
        for (int i = 0; i < content.size(); i++) {
            Warp warp = content.get(i);
            panel = panel.append(Component.newline()).append(Component.translatable("command.warp-list.entry").arguments(
                    Component.text(warp.name()), Component.text(warp.server()), Component.text(warp.location().world()), Component.text(warp.description())));
        }
        panel = panel.append(Component.newline()).append(CommandPanel.navigation(this.commandManager(), sender, this.getFeatureID(), page, String::valueOf));
        CommandPanel.send(this.commandManager(), sender, panel);
    }

    @Override
    public String getFeatureID() {
        return "warp-list";
    }
}
