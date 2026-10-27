package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.util.DateTimeUtils;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

final class HomePanels {
    private static final int PAGE_SIZE = 10;
    private static final Component NO_PERMISSION = Component.translatable("command.panel.no-permission");

    private HomePanels() {
    }

    static void list(CommandSender sender, SparrowPlayer owner, List<Home> homes, int requestedPage) {
        int pages = Math.max(1, (homes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(requestedPage, pages);
        int start = (page - 1) * PAGE_SIZE;
        CommandPanel.Builder panel = CommandPanel.builder().title(Component.translatable("command.home-list.title", Component.text(owner.name())));
        for (int i = start, end = Math.min(start + PAGE_SIZE, homes.size()); i < end; i++) {
            Home home = homes.get(i);
            String arguments = target(sender, owner, home);
            panel.field(Component.text(home.name()), Component.translatable("command.home-list.location", Component.text(home.server()), Component.text(home.location().world())));
            panel.buttons(
                    allowed(PanelButton.run(home.name(), "home", arguments), sender, owner, "home")
                            .disabledIf(!(sender instanceof Player), MessageConstants.COMMAND_PLAYER_REQUIRED),
                    allowed(PanelButton.run(Component.translatable("command.home.button.edit", Component.text(home.name())), "edit_home", arguments), sender, owner, "edit_home")
            );
        }
        panel.pagination(page, pages, "home_list", number -> owner.uniqueId() + " " + number).build().send(sender);
    }

    static void detail(CommandSender sender, SparrowPlayer owner, Home home) {
        WorldLocation location = home.location();
        String coordinates = location.x() + " " + location.y() + " " + location.z() + " " + location.yaw() + " " + location.pitch();
        String arguments = target(sender, owner, home);
        CommandPanel.builder()
                .title(Component.translatable("command.edit-home.title", Component.text(home.name())))
                .copyField(Component.translatable("command.edit-home.name"), home.name())
                .copyField(Component.translatable("command.edit-home.id"), home.id().toString())
                .field(Component.translatable("command.edit-home.owner"), owner.name())
                .copyField(Component.translatable("command.edit-home.owner-id"), owner.uniqueId().toString())
                .field(Component.translatable("command.edit-home.server"), home.server())
                .field(Component.translatable("command.edit-home.world"), location.world())
                .copyField(Component.translatable("command.edit-home.location"), coordinates)
                .field(Component.translatable("command.edit-home.created-at"), DateTimeUtils.fullTime(home.createdAt()))
                .field(Component.translatable("command.edit-home.updated-at"), DateTimeUtils.fullTime(home.updatedAt()))
                .buttons(
                        allowed(PanelButton.run(Component.translatable("command.home.button.teleport"), "home", arguments), sender, owner, "home")
                                .disabledIf(!(sender instanceof Player), MessageConstants.COMMAND_PLAYER_REQUIRED),
                        allowed(PanelButton.suggest(Component.translatable("command.home.button.rename"), "edit_home", arguments + " rename "), sender, owner, "edit_home")
                                .disabledIf(!sender.hasPermission(permission("edit_home") + ".rename"), NO_PERMISSION),
                        allowed(PanelButton.run(Component.translatable("command.home.button.relocate"), "edit_home", arguments + " relocate"), sender, owner, "edit_home")
                                .disabledIf(!sender.hasPermission(permission("edit_home") + ".relocate"), NO_PERMISSION)
                                .disabledIf(!(sender instanceof Player), MessageConstants.COMMAND_PLAYER_REQUIRED),
                        allowed(PanelButton.run(Component.translatable("command.home.button.delete"), "del_home", arguments), sender, owner, "del_home"),
                        allowed(PanelButton.run(Component.translatable("command.home.button.list"), "home_list", owner.uniqueId() + " 1"), sender, owner, "home_list")
                )
                .build()
                .send(sender);
    }

    private static String target(CommandSender sender, SparrowPlayer owner, Home home) {
        boolean self = sender instanceof Player player && player.getUniqueId().equals(owner.uniqueId());
        return self && !home.name().contains(".") ? home.name() : owner.uniqueId() + "." + home.name();
    }

    private static PanelButton allowed(PanelButton button, CommandSender sender, SparrowPlayer owner, String command) {
        boolean other = !(sender instanceof Player player) || !player.getUniqueId().equals(owner.uniqueId());
        return button.disabledIf(other && !sender.hasPermission(permission(command) + ".other"), NO_PERMISSION);
    }

    private static String permission(String command) {
        return SparrowPlugin.instance().configurationManager().commandsConfig().configDefinition().command(command).getPermission();
    }
}