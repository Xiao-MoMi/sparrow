package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.panel.PanelText;
import net.momirealms.sparrow.plugin.command.panel.PanelTone;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

final class HomePanels {
    private static final Component NO_PERMISSION = Component.translatable("command.panel.no-permission");

    private HomePanels() {
    }

    static CommandPanel list(CommandSender sender, SparrowPlayer owner, List<Home> homes, int requestedPage) {
        PanelText identity = PanelText.of(owner.name()).copy(owner.uniqueId().toString());
        Component go = Component.translatable("command.panel.go");
        Component edit = Component.translatable("command.panel.edit");
        CommandPanel.Builder panel = CommandPanel.list()
                .title(Component.translatable("command.home-list.title", identity.getComponent()))
                .status(PanelText.of(Component.translatable("command.panel.total", Component.text(homes.size()))).tone(PanelTone.MUTED))
                .actionColumn("go", go)
                .actionColumn("edit", edit);
        int pageSize = panel.getPageCapacity();
        int pages = Math.max(1, (homes.size() + pageSize - 1) / pageSize);
        int page = Math.min(requestedPage, pages);
        int start = (page - 1) * pageSize;
        if (homes.isEmpty()) {
            panel.entry(PanelText.of(Component.translatable("command.home-list.empty")).tone(PanelTone.MUTED));
        }
        for (int i = start, end = Math.min(start + pageSize, homes.size()); i < end; i++) {
            Home home = homes.get(i);
            WorldLocation location = home.location();
            String arguments = listTarget(sender, owner, home);
            Component details = Component.translatable(
                    "command.home-list.hover",
                    Component.text(home.name()),
                    Component.text(owner.name()),
                    Component.text(home.server()),
                    Component.text(location.world()),
                    PanelText.coordinates(location.x(), location.y(), location.z()).getComponent(),
                    PanelText.rotation(location.yaw(), location.pitch()).getComponent(),
                    PanelText.fullTime(home.createdAt()).getComponent(),
                    PanelText.fullTime(home.updatedAt()).getComponent(),
                    Component.text(home.id().toString())
            );
            panel.locationEntry(
                    PanelText.of(home.name()).copy(home.name()).hover(details),
                    PanelText.location(home.server(), location),
                    allowed(PanelButton.run(go, "home", arguments), sender, owner, "home")
                            .column("go")
                            .tone(PanelTone.SUCCESS)
                            .disabledIf(!(sender instanceof Player), MessageConstants.COMMAND_PLAYER_REQUIRED),
                    allowed(PanelButton.run(edit, "edit_home", arguments), sender, owner, "edit_home").column("edit")
            );
        }
        return panel.pagination(page, pages, "home_list", number -> owner.name() + " " + number).build();
    }

    private static String listTarget(CommandSender sender, SparrowPlayer owner, Home home) {
        boolean self = sender instanceof Player player && player.getUniqueId().equals(owner.uniqueId());
        if (self && !home.name().contains(".")) return home.name();
        String qualifier = owner.name().contains(".") ? owner.uniqueId().toString() : owner.name();
        return qualifier + "." + home.name();
    }

    static CommandPanel detail(CommandSender sender, SparrowPlayer owner, Home home) {
        WorldLocation location = home.location();
        String arguments = listTarget(sender, owner, home);
        Component rename = Component.translatable("command.panel.rename");
        Component go = Component.translatable("command.panel.go");
        Component relocate = Component.translatable("command.panel.relocate");
        Component delete = Component.translatable("command.panel.delete");
        Component times = Component.translatable("command.panel.audit-times", PanelText.time(home.createdAt()).getComponent(), PanelText.time(home.updatedAt()).getComponent());
        return CommandPanel.card()
                .title(PanelText.of(Component.translatable("command.edit-home.panel.title", Component.text(home.name()))).copy(home.id().toString()))
                .titleActions(
                        allowed(PanelButton.run(Component.translatable("command.panel.refresh"), "edit_home", arguments), sender, owner, "edit_home"),
                        allowed(PanelButton.run(Component.translatable("command.panel.list"), "home_list", owner.name() + " 1"), sender, owner, "home_list")
                )
                .actionColumn("primary", rename, go, relocate, delete)
                .field(
                        Component.translatable("command.panel.name"),
                        PanelText.of(home.name()).copy(home.name()),
                        allowed(PanelButton.suggest(rename, "edit_home", arguments + " rename "), sender, owner, "edit_home")
                                .disabledIf(!sender.hasPermission(permission("edit_home") + ".rename"), NO_PERMISSION)
                                .column("primary")
                )
                .field(Component.translatable("command.edit-home.panel.owner"), PanelText.of(owner.name()).copy(owner.uniqueId().toString()).tone(PanelTone.INFO))
                .field(
                        Component.translatable("command.panel.place"),
                        PanelText.location(home.server(), location),
                        allowed(PanelButton.run(go, "home", arguments), sender, owner, "home")
                                .disabledIf(!(sender instanceof Player), MessageConstants.COMMAND_PLAYER_REQUIRED)
                                .tone(PanelTone.SUCCESS)
                                .column("primary")
                )
                .field(
                        Component.translatable("command.panel.coordinates"),
                        PanelText.coordinates(location.x(), location.y(), location.z()),
                        allowed(PanelButton.run(relocate, "edit_home", arguments + " relocate"), sender, owner, "edit_home")
                                .disabledIf(!sender.hasPermission(permission("edit_home") + ".relocate"), NO_PERMISSION)
                                .disabledIf(!(sender instanceof Player), MessageConstants.COMMAND_PLAYER_REQUIRED)
                                .hint(Component.translatable("command.panel.relocate-hint"))
                                .column("primary")
                )
                .field(Component.translatable("command.panel.rotation"), PanelText.rotation(location.yaw(), location.pitch()))
                .field(
                        Component.translatable("command.panel.time"),
                        PanelText.of(times),
                        allowed(PanelButton.run(delete, "del_home", arguments), sender, owner, "del_home").tone(PanelTone.DANGER).column("primary")
                )
                .build();
    }

    private static PanelButton allowed(PanelButton button, CommandSender sender, SparrowPlayer owner, String command) {
        boolean other = !(sender instanceof Player player) || !player.getUniqueId().equals(owner.uniqueId());
        return button.disabledIf(other && !sender.hasPermission(permission(command) + ".other"), NO_PERMISSION);
    }

    private static String permission(String command) {
        return SparrowPlugin.instance().configurationManager().commandsConfig().configDefinition().command(command).getPermission();
    }
}