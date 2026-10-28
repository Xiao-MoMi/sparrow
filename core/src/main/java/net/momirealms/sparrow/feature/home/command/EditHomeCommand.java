package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class EditHomeCommand extends AbstractHomeCommand {

    public EditHomeCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin, @NotNull HomeFeature feature) {
        super(manager, plugin, feature);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> named = builder.required("name", TokenParser.tokenParser(), this::suggest);
        manager.command(named.futureHandler(this::execute));
        manager.command(named.literal("rename")
                .required("new_name", TokenParser.tokenParser())
                .permission(Permission.allOf(builder.commandPermission(), Permission.of(this.commandConfig().getPermission() + ".rename")))
                .futureHandler(this::rename));
        manager.command(named.literal("relocate")
                .permission(Permission.allOf(builder.commandPermission(), Permission.of(this.commandConfig().getPermission() + ".relocate")))
                .senderType(Player.class)
                .futureHandler(this::relocate));
    }

    private CompletableFuture<Void> execute(CommandContext<CommandSender> context) {
        return this.withHome(context, (owner, home) -> {
            HomePanels.detail(context.sender(), owner, home).send(context.sender());
            return CompletableFuture.completedFuture(null);
        });
    }

    private CompletableFuture<Void> rename(CommandContext<CommandSender> context) {
        String name = context.get("new_name");
        return this.withHome(context, (owner, home) -> this.feature.service().rename(owner.uniqueId(), home.id(), name)
                .thenAccept(result -> this.saved(
                        context,
                        owner,
                        result,
                        name,
                        MessageConstants.COMMAND_EDIT_HOME_RENAMED,
                        Component.text(home.name()),
                        Component.text(name),
                        Component.text(owner.name())
                )));
    }

    private CompletableFuture<Void> relocate(CommandContext<Player> context) {
        WorldLocation location = WorldLocation.from(context.sender().getLocation());
        return this.withHome(context, (owner, home) -> this.feature.service().relocate(owner.uniqueId(), home.id(), ServerConfig.serverId(), location)
                .thenAccept(result -> this.saved(
                        context,
                        owner,
                        result,
                        home.name(),
                        MessageConstants.COMMAND_EDIT_HOME_RELOCATED,
                        Component.text(home.name()),
                        Component.text(owner.name())
                )));
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "edit_home";
    }
}