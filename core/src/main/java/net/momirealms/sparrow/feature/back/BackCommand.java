package net.momirealms.sparrow.feature.back;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.teleport.TeleportOptions;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
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

public final class BackCommand extends BukkitCommandFeature {

    public BackCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
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
        TeleportOptions options = back.config().teleportOptions().resolve(player, player == context.sender());
        WorldLocation point = back.point(player.getUniqueId());
        CompletableFuture<Void> transfer;
        if (point != null) {
            transfer = this.send(context, player, ServerConfig.serverId(), point, options);
        } else {
            // 本服没有记录时才查询上一个服务器的下线位置, 玩家还没完成进服处理时无法判断是否刚切服
            BukkitSparrowPlayer sparrow = this.plugin().playerManager().getPlayer(player);
            transfer = this.plugin().dataStorage()
                    .loadPlayer(player.getUniqueId())
                    .thenCompose(found -> {
                        if (sparrow == null || found.isEmpty() || !back.switchedFrom(sparrow, found.get())) {
                            this.handleFeedback(
                                    context,
                                    (player == context.sender() ? MessageConstants.COMMAND_BACK_NONE_SELF : MessageConstants.COMMAND_BACK_NONE),
                                    Component.text(player.getName())
                            );
                            return CompletableFuture.completedFuture(null);
                        }
                        return this.send(context, player, found.get().lastLogoutServer(), found.get().lastLogoutLocation(), options);
                    });
        }
        transfer.exceptionally(error -> {
            Throwable cause = error instanceof CompletionException ? error.getCause() : error;
            if (cause instanceof TimeoutException) {
                this.handleFeedback(context, MessageConstants.COMMAND_BACK_TIMEOUT);
            } else {
                this.plugin().logger().warn("Failed to send " + player.getName() + " back", cause);
                this.handleFeedback(
                        context,
                        (player == context.sender() ? MessageConstants.COMMAND_TELEPORT_FAILURE_SELF : MessageConstants.COMMAND_TELEPORT_FAILURE),
                        Component.text(player.getName())
                );
            }
            return null;
        });
    }

    private CompletableFuture<Void> send(
            CommandContext<CommandSender> context,
            Player player,
            String server,
            WorldLocation location,
            TeleportOptions options
    ) {
        return this.plugin().teleportService()
                .teleport(player, server, location, options)
                .thenAccept(result -> {
                    TranslatableComponent message = switch (result) {
                        case LOCAL_SUCCESS -> (player == context.sender() ? MessageConstants.COMMAND_BACK_SUCCESS_SELF : MessageConstants.COMMAND_BACK_SUCCESS);
                        case REMOTE_SUCCESS -> player == context.sender() ? null : MessageConstants.COMMAND_BACK_SUCCESS;
                        case SERVER_OFFLINE -> MessageConstants.COMMAND_BACK_SERVER_OFFLINE;
                        case INVALID -> MessageConstants.COMMAND_BACK_INVALID;
                        case FAILED -> (player == context.sender() ? MessageConstants.COMMAND_TELEPORT_FAILURE_SELF : MessageConstants.COMMAND_TELEPORT_FAILURE);
                        case COOLDOWN, CANCELLED -> null;
                    };
                    if (message != null) {
                        this.handleFeedback(context, message, Component.text(player.getName()), Component.text(server));
                    }
                });
    }

    @Override
    public String getFeatureID() {
        return "back";
    }
}
