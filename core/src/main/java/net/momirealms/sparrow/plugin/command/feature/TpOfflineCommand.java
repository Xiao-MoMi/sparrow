package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public final class TpOfflineCommand extends BukkitCommandFeature {

    public TpOfflineCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerManager().cluster()))
                .optional("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        MultiplePlayerSelector selector = context.getOrDefault("targets", null);
        Collection<Player> targets;
        if (selector != null) {
            targets = selector.values();
        } else if (context.sender() instanceof Player player) {
            targets = List.of(player);
        } else {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        if (targets.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        String name = context.get("player");
        this.plugin().dataStorage().lookupUser(name)
                .thenCompose(found -> found.map(this.plugin().dataStorage()::loadPlayer).orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())))
                .thenCompose(found -> {
                    if (found.isEmpty()) {
                        this.handleFeedback(context, MessageConstants.COMMAND_TP_OFFLINE_UNKNOWN, Component.text(name));
                        return CompletableFuture.completedFuture(null);
                    }
                    PlayerData data = found.get();
                    if (data.lastLogoutLocation() == null || data.lastLogoutServer() == null) {
                        this.handleFeedback(context, MessageConstants.COMMAND_TP_OFFLINE_NO_LOCATION, Component.text(name));
                        return CompletableFuture.completedFuture(null);
                    }
                    List<CompletableFuture<Void>> transfers = targets.stream().map(player -> this.plugin().playerManager().teleports().transfer(player, data.lastLogoutServer(), data.lastLogoutLocation()).thenAccept(result -> {
                        var message = switch (result) {
                            case SUCCESS -> MessageConstants.COMMAND_TP_OFFLINE_SUCCESS;
                            case CONNECTING -> MessageConstants.COMMAND_TP_OFFLINE_CONNECTING;
                            case SERVER_OFFLINE -> MessageConstants.COMMAND_TP_OFFLINE_SERVER_OFFLINE;
                            case INVALID -> MessageConstants.COMMAND_TP_OFFLINE_INVALID;
                            case FAILED -> MessageConstants.COMMAND_TELEPORT_FAILURE;
                        };
                        this.handleFeedback(context, message, Component.text(player.getName()), Component.text(name), Component.text(data.lastLogoutServer()));
                    })).toList();
                    return CompletableFuture.allOf(transfers.toArray(CompletableFuture[]::new));
                }).exceptionally(error -> {
                    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                    if (cause instanceof TimeoutException) {
                        this.handleFeedback(context, MessageConstants.COMMAND_TP_OFFLINE_TIMEOUT);
                    } else {
                        this.plugin().logger().warn("Failed to teleport to the saved location of " + name, cause);
                        this.handleFeedback(context, MessageConstants.COMMAND_TELEPORT_FAILURE, Component.text(name));
                    }
                    return null;
                });
    }

    @Override
    public String getFeatureID() {
        return "tp-offline";
    }
}
