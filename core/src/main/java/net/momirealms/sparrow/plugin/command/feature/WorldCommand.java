package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.util.WorldUtils;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.EntityUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.WorldParser;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class WorldCommand extends BukkitCommandFeature {
    public WorldCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.optional("world", WorldParser.worldParser())
                .optional("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .handler(this::execute);
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
            this.plugin().scheduler().platform().run(() -> {
                World target = selected != null ? selected : WorldUtils.next(player.getWorld());
                if (target == null) {
                    this.handleFeedback(context, MessageConstants.COMMAND_WORLD_EMPTY);
                    return;
                }
                Location destination = WorldUtils.destination(player.getLocation(), target);
                int height = (int) Math.ceil(player.getHeight());
                this.plugin().scheduler().platform().run(() -> {
                    int space = 0;
                    while (destination.getY() < target.getMaxHeight()) {
                        Block block = destination.getBlock();
                        space = block.isPassable() && !block.isLiquid() ? space + 1 : 0;
                        if (space >= height) {
                            break;
                        }
                        destination.add(0, 1, 0);
                    }
                    if (space < height) {
                        this.handleFeedback(context, MessageConstants.COMMAND_WORLD_NO_SPACE, Component.text(target.getName()));
                        return;
                    }
                    destination.subtract(0, height - 1, 0);
                    this.plugin().scheduler().platform().run(() -> {
                        EntityUtils.teleport(player, destination).whenComplete((success, error) -> {
                            if (error != null) {
                                this.plugin().logger().warn("Failed to change world for " + player.getName(), error);
                            }
                            this.handleFeedback(context, error == null && success ? MessageConstants.COMMAND_WORLD_SUCCESS : MessageConstants.COMMAND_TELEPORT_FAILURE,
                                    Component.text(player.getName()), Component.text(target.getName()));
                        });
                    }, () -> {}, player);
                }, target, destination.getBlockX() >> 4, destination.getBlockZ() >> 4);
            }, () -> {}, player);
        }
    }

    @Override
    public String getFeatureID() {
        return "world";
    }
}
