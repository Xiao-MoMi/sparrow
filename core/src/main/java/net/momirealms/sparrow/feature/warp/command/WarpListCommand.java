package net.momirealms.sparrow.feature.warp.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.feature.warp.WarpFeature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
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
            if (this.feature.visible(sender, warp)) {
                visible.add(warp);
            }
        }
        int pages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int index = Math.min(context.<Integer>getOrDefault("page", 1), pages) - 1;
        int from = index * PAGE_SIZE;
        TextPage<Warp> page = new TextPage<>(index, PAGE_SIZE, visible.size(), visible.subList(from, Math.min(from + PAGE_SIZE, visible.size())));
        CommandPanel panel = new CommandPanel(this.commandManager(), sender).header(Component.translatable("command.warp-list.title"), page);
        if (page.content().isEmpty()) {
            panel.empty();
        }
        List<Warp> content = page.content();
        int contentSize = content.size();
        for (int i = 0; i < contentSize; i++) {
            Warp warp = content.get(i);
            Component entry = Component.translatable(
                    "command.warp-list.entry",
                    panel.run(Component.text(warp.name()), "warp", warp.name()).build(),
                    Component.text(warp.server()),
                    Component.text(warp.location().world()),
                    Component.text(warp.description())
            );
            PanelButton edit = panel.run(CommandPanel.label("edit"), "edit-warp", warp.name());
            panel.line(edit.available() ? entry.append(Component.space()).append(edit.build()) : entry);
        }
        panel.navigation(this.getFeatureID(), page, String::valueOf).send();
    }

    @Override
    public String getFeatureID() {
        return "warp-list";
    }
}
