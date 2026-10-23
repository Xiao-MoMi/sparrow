package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.ClearInventoryRequest;
import net.momirealms.sparrow.player.message.ClearInventoryResponse;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ClearInvCommand extends BukkitCommandFeature {

    public ClearInvCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", GlobalPlayerParser.clusterPlayerParser()).handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        OfflineSparrowPlayer target = this.resolveOnlinePlayer(context, context.get("player"));
        if (target == null) return;
        ClearInventoryRequest request = new ClearInventoryRequest(target.uniqueId(), false);
        CompletableFuture<ClearInventoryResponse> resultFuture;
        if (target instanceof BukkitSparrowPlayer) {
            resultFuture = request.execute();
        } else {
            String server = target.getCurrentServerId();
            if (server == null) {
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_OFFLINE, Component.text(target.name()));
                return;
            }
            resultFuture = this.plugin().messageBrokerManager().broker()
                    .publishTwoWay(request, server)
                    .orTimeout(5, TimeUnit.SECONDS);
        }
        resultFuture.whenComplete((result, error) -> {
            if (error != null) {
                this.plugin().logger().warn("Failed to execute ClearInv for " + target.name(), error);
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUEST_FAILED, Component.text(target.name()));
                return;
            }
            if (!result.online()) {
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_OFFLINE, Component.text(target.name()));
            } else {
                this.handleFeedback(context, MessageConstants.COMMAND_CLEAR_INV_SUCCESS, Component.text(target.name()), Component.text(result.count()));
            }
        });
    }

    @Override
    public String getFeatureID() {
        return "clear_inv";
    }
}