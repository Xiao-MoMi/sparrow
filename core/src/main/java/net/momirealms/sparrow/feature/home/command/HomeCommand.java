package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import net.momirealms.sparrow.teleport.TeleportType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public final class HomeCommand extends AbstractHomeCommand {

    public HomeCommand(HomeFeature feature) {
        super(feature);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class)
                .optional(
                        "name",
                        TokenParser.tokenParser(),
                        (context, input) -> super.feature.suggest(context.sender(), input.peekString(), true, this.commandConfig().getPermission())
                )
                .handler(this::execute));
    }

    private void execute(CommandContext<Player> context) {
        Player player = context.sender();
        this.target(player, context.getOrDefault("name", null))
                .thenCompose(found -> {
                    if (found.isEmpty()) {
                        return CompletableFuture.completedFuture(null);
                    }
                    Target target = found.get();
                    String name = target.name();
                    return super.feature.service()
                            .snapshot(target.owner().uniqueId())
                            .thenAccept(snapshot -> {
                                Home home = name == null ? snapshot.get(super.feature.config().defaultName()) : snapshot.get(name);
                                if (name == null && home == null) {
                                    if (snapshot.size() == 1) {
                                        home = snapshot.homes().getFirst();
                                    } else if (snapshot.size() == 0) {
                                        this.handleFeedback(
                                                context,
                                                MessageConstants.COMMAND_HOME_EMPTY,
                                                Component.text(super.feature.usage("set-home"))
                                        );
                                        return;
                                    } else {
                                        this.handleFeedback(
                                                context,
                                                MessageConstants.COMMAND_HOME_NAME_REQUIRED,
                                                Component.text(super.feature.usage("home"))
                                        );
                                        return;
                                    }
                                }
                                if (home == null) {
                                    this.handleFeedback(context, MessageConstants.COMMAND_HOME_UNKNOWN, Component.text(name));
                                    return;
                                }
                                this.teleport(context, home);
                            });
                })
                .exceptionally(error -> {
                    this.failed(player, error); return null;
                });
    }

    private void teleport(CommandContext<Player> context, Home home) {
        Player player = context.sender();
        this.plugin().teleportService()
                .teleport(player, TeleportType.HOME, home.server(), home.location(), true)
                .thenAccept(result -> {
                    TranslatableComponent message = switch (result) {
                        case LOCAL_SUCCESS -> MessageConstants.COMMAND_HOME_SUCCESS;
                        case SERVER_OFFLINE -> MessageConstants.COMMAND_HOME_SERVER_OFFLINE;
                        case INVALID -> MessageConstants.COMMAND_HOME_INVALID;
                        case FAILED -> MessageConstants.COMMAND_TELEPORT_FAILURE_SELF;
                        case REMOTE_SUCCESS, REJECTED -> null;
                    };
                    if (message != null) {
                        this.handleFeedback(context, message, Component.text(home.name()), Component.text(home.server()));
                    }
                })
                .exceptionally(error -> {
                    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                    if (cause instanceof TimeoutException) {
                        this.handleFeedback(context, MessageConstants.COMMAND_HOME_TIMEOUT);
                    } else {
                        this.plugin()
                                .logger()
                                .warn("Failed to teleport " + player.getName() + " to home " + home.name(), cause);
                        this.handleFeedback(context, MessageConstants.COMMAND_TELEPORT_FAILURE_SELF);
                    }
                    return null;
                });
    }

    @Override
    public String getFeatureID() {
        return "home";
    }
}
