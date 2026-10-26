package net.momirealms.sparrow.feature.warp.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.feature.warp.WarpFeature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class WarpListCommand extends BukkitCommandFeature {
    private static final int PAGE_SIZE = 10;

    private final WarpFeature feature;

    public WarpListCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin, @NotNull WarpFeature feature) {
        super(manager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.optional("page", IntegerParser.integerParser(1)).handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        List<Warp> visible = this.feature.registry().all().stream().filter(warp -> this.feature.visible(context.sender(), warp)).toList();
        int pages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(context.<Integer>getOrDefault("page", 1), pages);
        int start = (page - 1) * PAGE_SIZE;
        CommandPanel.Builder panel = CommandPanel.builder().title(Component.translatable("command.warp-list.title"));
        for (int i = start, end = Math.min(start + PAGE_SIZE, visible.size()); i < end; i++) {
            Warp warp = visible.get(i);
            panel.field(
                    Component.text(warp.name()),
                    Component.translatable("command.warp-list.location", Component.text(warp.server()), Component.text(warp.location().world()))
            );
            panel.buttons(PanelButton.run(warp.name(), "warp", warp.name()));
        }
        panel.buttons(PanelButton.suggest(Component.translatable("command.warp.button.edit"), "edit_warp", ""))
                .pagination(page, pages, this.getFeatureID(), String::valueOf)
                .build()
                .send(context.sender());
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "warp_list";
    }
}