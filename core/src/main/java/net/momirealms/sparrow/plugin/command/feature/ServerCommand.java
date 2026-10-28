package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BatchCommandFeedback;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import net.momirealms.sparrow.plugin.command.parser.ServerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public final class ServerCommand extends BukkitCommandFeature {

    public ServerCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> command = builder.required("server", ServerParser.all());
        manager.command(command.required("targets", GlobalPlayerParser.clusterPlayersParser())
                .permission(this.otherPermission(command))
                .handler(this::execute));
        manager.command(command.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String server = context.get("server");
        List<String> targets = context.getOrDefault("targets", null);
        if (targets == null) {
            if (!(context.sender() instanceof Player player)) {
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
                return;
            }
            targets = List.of(player.getUniqueId().toString());
        }
        BatchCommandFeedback feedback = new BatchCommandFeedback(this, context, targets.size());
        for (String input : targets) {
            OfflineSparrowPlayer target = this.resolveOnlinePlayer(context, input, MessageConstants.COMMAND_SERVER_PLAYER_OFFLINE, feedback);
            if (target == null) continue;
            this.connect(context, target, server, feedback);
        }
    }

    private void connect(CommandContext<CommandSender> context, OfflineSparrowPlayer target, String server, BatchCommandFeedback feedback) {
        String name = target.name();
        if (server.equals(target.getCurrentServerId())) {
            feedback.success(MessageConstants.COMMAND_SERVER_CURRENT, Component.text(name), Component.text(server));
            return;
        }
        boolean self = context.sender() instanceof Player sender && sender.getUniqueId().equals(target.uniqueId());
        if (self) {
            feedback.progress(MessageConstants.COMMAND_SERVER_CONNECTING_SELF, Component.text(server));
        }
        target.connect(server).whenComplete((result, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                if (cause instanceof TimeoutException) {
                    feedback.failure(MessageConstants.COMMAND_SERVER_TIMEOUT, Component.text(name), Component.text(server));
                } else {
                    this.plugin().logger().warn("Failed to connect " + name + " to " + server, cause);
                    feedback.failure(MessageConstants.COMMAND_SERVER_FAILED, Component.text(name), Component.text(server));
                }
                return;
            }
            switch (result) {
                case SUCCESS -> {
                    if (!self) {
                        feedback.success(MessageConstants.COMMAND_SERVER_SUCCESS, Component.text(name), Component.text(server));
                    } else {
                        feedback.successWithoutMessage();
                    }
                }
                case PLAYER_OFFLINE -> feedback.failure(MessageConstants.COMMAND_SERVER_PLAYER_OFFLINE, Component.text(name));
                case SERVER_NOT_FOUND -> feedback.failureFor(name, MessageConstants.COMMAND_SERVER_UNKNOWN, Component.text(server));
                case FAILED -> feedback.failure(MessageConstants.COMMAND_SERVER_FAILED, Component.text(name), Component.text(server));
            }
        });
    }

    @Override
    public String getFeatureID() {
        return "server";
    }
}