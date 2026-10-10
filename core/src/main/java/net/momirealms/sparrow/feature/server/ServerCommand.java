package net.momirealms.sparrow.feature.server;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ServerParser;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.ParserDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public final class ServerCommand extends BukkitCommandFeature {
    private final ServerParser<CommandSender> parser;

    public ServerCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
        this.parser = new ServerParser<>(server -> this.plugin().featureManager().feature(ServerFeature.ID, ServerFeature.class).allowed(server));
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> command = builder.required("server", ParserDescriptor.of(this.parser, String.class));
        manager.command(command.required("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .permission(this.otherPermission(command))
                .handler(this::execute));
        manager.command(command.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        ServerFeature feature = this.plugin().featureManager().feature(ServerFeature.ID, ServerFeature.class);
        String server = context.get("server");
        if (!feature.allowed(server)) {
            this.handleFeedback(context, MessageConstants.COMMAND_SERVER_NOT_ALLOWED, Component.text(server));
            return;
        }
        if (server.equals(ServerConfig.serverId())) {
            this.handleFeedback(context, MessageConstants.COMMAND_SERVER_CURRENT, Component.text(server));
            return;
        }
        MultiplePlayerSelector selector = context.getOrDefault("targets", null);
        Collection<Player> players;
        if (selector != null) {
            players = selector.values();
        } else if (context.sender() instanceof Player player) {
            players = List.of(player);
        } else {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        if (players.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        for (Player player : players) {
            this.connect(context, player, server);
        }
    }

    private void connect(CommandContext<CommandSender> context, Player player, String server) {
        String name = player.getName();
        SparrowPlayer sparrow = this.plugin().playerManager().getPlayer(player);
        if (sparrow == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_SERVER_PLAYER_OFFLINE, Component.text(name));
            return;
        }
        boolean self = player == context.sender();
        if (self) {
            this.handleFeedback(context, MessageConstants.COMMAND_SERVER_CONNECTING_SELF, Component.text(server));
        }
        sparrow.connect(server).whenComplete((result, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                if (cause instanceof TimeoutException) {
                    this.handleFeedback(context, MessageConstants.COMMAND_SERVER_TIMEOUT, Component.text(name), Component.text(server));
                } else {
                    this.plugin().logger().warn("Failed to connect " + name + " to " + server, cause);
                    this.handleFeedback(context, MessageConstants.COMMAND_SERVER_FAILED, Component.text(name), Component.text(server));
                }
                return;
            }
            switch (result) {
                case SUCCESS -> {
                    if (!self) {
                        this.handleFeedback(context, MessageConstants.COMMAND_SERVER_SUCCESS, Component.text(name), Component.text(server));
                    }
                }
                case PLAYER_OFFLINE -> this.handleFeedback(context, MessageConstants.COMMAND_SERVER_PLAYER_OFFLINE, Component.text(name));
                case SERVER_NOT_FOUND -> this.handleFeedback(context, MessageConstants.COMMAND_SERVER_UNKNOWN, Component.text(server));
                case FAILED -> this.handleFeedback(context, MessageConstants.COMMAND_SERVER_FAILED, Component.text(name), Component.text(server));
            }
        });
    }

    @Override
    public String getFeatureID() {
        return "server";
    }
}