package net.momirealms.sparrow.feature.warp.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.feature.warp.WarpFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.panel.PanelText;
import net.momirealms.sparrow.plugin.command.panel.PanelTone;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class WarpListCommand extends BukkitCommandFeature {
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
        this.createPanel(context.sender(), visible, context.getOrDefault("page", 1)).send(context.sender());
    }

    private CommandPanel createPanel(CommandSender sender, List<Warp> visible, int requestedPage) {
        Component go = Component.translatable("command.panel.go");
        Component edit = Component.translatable("command.panel.edit");
        CommandPanel.Builder panel = CommandPanel.list()
                .title(Component.translatable("command.warp-list.title"))
                .status(PanelText.of(Component.translatable("command.panel.total", Component.text(visible.size()))).tone(PanelTone.MUTED))
                .actionColumn("go", go)
                .actionColumn("edit", edit);
        int pageSize = panel.getPageCapacity();
        int pages = Math.max(1, (visible.size() + pageSize - 1) / pageSize);
        int page = Math.min(requestedPage, pages);
        int start = (page - 1) * pageSize;
        if (visible.isEmpty()) {
            panel.entry(PanelText.of(Component.translatable("command.warp-list.empty")).tone(PanelTone.MUTED));
        }
        for (int i = start, end = Math.min(start + pageSize, visible.size()); i < end; i++) {
            Warp warp = visible.get(i);
            WorldLocation location = warp.location();
            Component details = Component.translatable(
                    "command.warp-list.hover",
                    Component.text(warp.name()),
                    Component.text(warp.server()),
                    Component.text(location.world()),
                    PanelText.coordinates(location.x(), location.y(), location.z()).getComponent(),
                    PanelText.rotation(location.yaw(), location.pitch()).getComponent(),
                    PanelText.fullTime(warp.createdAt()).getComponent(),
                    PanelText.fullTime(warp.updatedAt()).getComponent(),
                    Component.text(warp.id().toString())
            );
            panel.locationEntry(
                    PanelText.of(warp.name()).copy(warp.name()).hover(details),
                    PanelText.location(warp.server(), location),
                    PanelButton.run(go, "warp", warp.name())
                            .column("go")
                            .tone(PanelTone.SUCCESS)
                            .disabledIf(!(sender instanceof Player), MessageConstants.COMMAND_PLAYER_REQUIRED),
                    PanelButton.run(edit, "edit_warp", warp.name()).column("edit")
            );
        }
        return panel.pagination(page, pages, this.getFeatureID(), String::valueOf).build();
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "warp_list";
    }
}