package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.compatibility.CompatibilityManager;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.feature.home.HomeSnapshot;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.PlayerIdentity;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.panel.TextPage;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class HomeListCommand extends AbstractHomeCommand {
    private static final int PAGE_SIZE = 10;

    public HomeListCommand(HomeFeature feature) {
        super(feature);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.optional("page", IntegerParser.integerParser(1)).handler(this::execute));
        manager.command(builder.literal("other")
                .required("player", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerDirectory()))
                .optional("page", IntegerParser.integerParser(1))
                .permission(this.otherPermission(builder))
                .handler(this::execute));
    }

    private boolean available(CommandSender sender, boolean other) {
        String permission = this.commandConfig().getPermission();
        return this.commandConfig().isEnable() && this.commandConfig().getUsages().stream().anyMatch(usage -> usage.startsWith("/"))
                && (permission == null || permission.isEmpty() || sender.hasPermission(permission))
                && (!other || sender.hasPermission(permission + ".other"));
    }

    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        this.owner(sender, context.getOrDefault("player", null))
                .thenCompose(owner -> {
                    if (owner.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return super.feature.service()
                            .snapshot(owner.get().uuid())
                            .thenAccept(snapshot -> this.show(sender, owner.get(), snapshot, context.getOrDefault("page", 1)));
                })
                .exceptionally(error -> {
                    this.failed(sender, error); return null;
                });
    }

    private void show(CommandSender sender, PlayerIdentity owner, HomeSnapshot snapshot, int requestedPage) {
        boolean self = sender instanceof Player player && player.getUniqueId().equals(owner.uuid());
        if (!this.available(sender, !self)) {
            this.handleFeedback(sender, MessageConstants.COMMAND_HOME_NO_PERMISSION);
            return;
        }
        Component limit = Component.translatable("command.home-list.limit-unknown");
        if (self) {
            int maximum = super.feature.limit((Player) sender);
            limit = maximum == CompatibilityManager.UNLIMITED ? Component.translatable("command.home-list.unlimited") : Component.text(maximum);
        }
        int pages = Math.max(1, (snapshot.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int index = Math.min(requestedPage, pages) - 1;
        int from = index * PAGE_SIZE;
        List<Home> content = snapshot.homes().subList(from, Math.min(from + PAGE_SIZE, snapshot.size()));
        TextPage<Home> page = new TextPage<>(index, PAGE_SIZE, snapshot.size(), content);
        CommandPanel panel = new CommandPanel(this.commandManager(), sender)
                .header(Component.translatable("command.home-list.title", Component.text(owner.name()), limit), page);
        if (content.isEmpty()) {
            panel.empty();
        }
        for (int i = 0; i < content.size(); i++) {
            Home home = content.get(i);
            String target = self ? home.name() : owner.name() + "." + home.name();
            PanelButton travel = panel.suggest(Component.text(home.name()), "home", target).playersOnly();
            PanelButton edit = panel.suggest(CommandPanel.label("edit"), "edit-home", target);
            if (!self) {
                travel.permission(super.feature.permission("home") + ".other");
                edit.permission(super.feature.permission("edit-home") + ".other");
            }
            Component entry = Component.translatable(
                    "command.home-list.entry",
                    travel.build(),
                    Component.text(home.server()),
                    Component.text(home.location().world())
            );
            panel.line(edit.available() ? entry.append(Component.space()).append(edit.build()) : entry);
        }
        panel.navigation(this.getFeatureID(), page, number -> self ? String.valueOf(number) : "other " + owner.name() + " " + number, true).send();
    }

    @Override
    public String getFeatureID() {
        return "home-list";
    }
}