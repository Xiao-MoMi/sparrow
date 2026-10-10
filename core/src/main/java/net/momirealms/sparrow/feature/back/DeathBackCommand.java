package net.momirealms.sparrow.feature.back;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.teleport.TeleportOptions;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public final class DeathBackCommand extends BukkitCommandFeature {

    public DeathBackCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", PlayerParser.playerParser())
                .permission(this.otherPermission(builder))
                .handler(this::execute));
        manager.command(builder.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        BackFeature back = this.plugin().featureManager().feature(BackFeature.ID, BackFeature.class);
        boolean self = player == context.sender();
        TeleportOptions options = back.config().deathTeleportOptions().resolve(player, self);
        back.loadDeath(player.getUniqueId())
                .thenCompose(found -> {
                    if (found.isEmpty() || found.get().lastDeathLocation() == null) {
                        this.handleFeedback(context, self ? MessageConstants.COMMAND_DEATH_BACK_NONE_SELF : MessageConstants.COMMAND_DEATH_BACK_NONE, Component.text(player.getName()));
                        return CompletableFuture.completedFuture(null);
                    }
                    PlayerData data = found.get();
                    return this.send(context, player, data.lastDeathServer(), data.lastDeathLocation(), options);
                })
                .exceptionally(error -> {
                    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                    if (cause instanceof TimeoutException) {
                        this.handleFeedback(context, MessageConstants.COMMAND_DEATH_BACK_TIMEOUT);
                    } else {
                        this.handleFeedback(context, self ? MessageConstants.COMMAND_TELEPORT_FAILURE_SELF : MessageConstants.COMMAND_TELEPORT_FAILURE, Component.text(player.getName()));
                    }
                    return null;
                });
    }

    @NotNull
    private CompletableFuture<Void> send(
            @NotNull CommandContext<CommandSender> context,
            @NotNull Player player,
            @NotNull String server,
            @NotNull WorldLocation location,
            @NotNull TeleportOptions options
    ) {
        boolean self = player == context.sender();
        return this.plugin().teleportService()
                .teleport(player, server, location, options)
                .thenAccept(result -> {
                    TranslatableComponent message = switch (result) {
                        case LOCAL_SUCCESS -> (self ? MessageConstants.COMMAND_DEATH_BACK_SUCCESS_SELF : MessageConstants.COMMAND_DEATH_BACK_SUCCESS);
                        case REMOTE_SUCCESS -> self ? null : MessageConstants.COMMAND_DEATH_BACK_SUCCESS;
                        case SERVER_OFFLINE -> MessageConstants.COMMAND_DEATH_BACK_SERVER_OFFLINE;
                        case INVALID -> MessageConstants.COMMAND_DEATH_BACK_INVALID;
                        case FAILED -> (self ? MessageConstants.COMMAND_TELEPORT_FAILURE_SELF : MessageConstants.COMMAND_TELEPORT_FAILURE);
                        case COOLDOWN, CANCELLED -> null;
                    };
                    if (message != null) {
                        this.handleFeedback(context, message, Component.text(player.getName()), Component.text(server));
                    }
                })
                .whenComplete((ignored, error) -> {
                    if (error == null) return;
                    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                    if (!(cause instanceof TimeoutException)) {
                        this.plugin().logger().warn("Failed to send " + player.getName() + " to their last death location", cause);
                    }
                });
    }

    @Override
    public String getFeatureID() {
        return "death-back";
    }
}
