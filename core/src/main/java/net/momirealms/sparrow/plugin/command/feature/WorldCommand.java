package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.util.WorldUtils;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.teleport.WorldTeleportSearch;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.WorldParser;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class WorldCommand extends BukkitCommandFeature {

    public WorldCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> command = builder.required("world", WorldParser.worldParser());
        manager.command(command.required("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .permission(this.otherPermission(command))
                .handler(this::execute));
        manager.command(command.handler(this::execute));
        manager.command(builder.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
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
        World selected = context.getOrDefault("world", null);
        for (Player player : players) {
            World target = selected != null ? selected : WorldUtils.next(player.getWorld());
            if (target == null) {
                this.handleFeedback(context, MessageConstants.COMMAND_WORLD_EMPTY);
                continue;
            }
            Location origin = WorldUtils.destination(player.getLocation(), target);
            WorldTeleportSearch search = new WorldTeleportSearch(origin, (int) Math.ceil(player.getHeight()));
            search.find().thenCompose(destination -> {
                if (destination == null) {
                    this.handleFeedback(context, MessageConstants.COMMAND_WORLD_NO_SPACE, Component.text(target.getName()));
                    return CompletableFuture.<TeleportResult>completedFuture(null);
                }
                return this.plugin().teleportService().transfer(player, ServerConfig.serverId(), destination);
            }).whenComplete((result, error) -> {
                if (error == null && result == null) return;
                if (error != null) {
                    this.plugin().logger().warn("Failed to change world for " + player.getName(), error);
                }
                boolean self = player == context.sender();
                TranslatableComponent message = error == null && result == TeleportResult.LOCAL_SUCCESS
                        ? (self ? MessageConstants.COMMAND_WORLD_SUCCESS_SELF : MessageConstants.COMMAND_WORLD_SUCCESS)
                        : (self ? MessageConstants.COMMAND_TELEPORT_FAILURE_SELF : MessageConstants.COMMAND_TELEPORT_FAILURE);
                this.handleFeedback(context, message, Component.text(player.getName()), Component.text(target.getName()));
            });
        }
    }

    @Override
    public String getFeatureID() {
        return "world";
    }
}