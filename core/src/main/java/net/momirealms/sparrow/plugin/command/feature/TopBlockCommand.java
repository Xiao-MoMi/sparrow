package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.EntityUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultipleEntitySelector;
import org.incendo.cloud.bukkit.parser.selector.MultipleEntitySelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class TopBlockCommand extends BukkitCommandFeature {
    public TopBlockCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.optional("targets", MultipleEntitySelectorParser.multipleEntitySelectorParser()).handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        MultipleEntitySelector selector = context.getOrDefault("targets", null);
        Collection<Entity> entities;
        if (selector != null) {
            entities = selector.values();
        } else if (context.sender() instanceof Player player) {
            entities = List.of(player);
        } else {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        if (entities.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        for (Entity entity : entities) {
            this.plugin().scheduler().platform().run(() -> {
                Location location = entity.getLocation();
                World world = location.getWorld();
                Block block = world.getHighestBlockAt(location.getBlockX(), location.getBlockZ());
                int y = block.isPassable() ? block.getY() : block.getY() + 1;
                if (y < world.getMinHeight() || y + Math.ceil(entity.getHeight()) > world.getMaxHeight()
                        || !world.getBlockAt(location.getBlockX(), y, location.getBlockZ()).isPassable()
                        || !world.getBlockAt(location.getBlockX(), y + 1, location.getBlockZ()).isPassable()
                        || block.isEmpty()) {
                    this.handleFeedback(context, MessageConstants.COMMAND_TOP_BLOCK_UNAVAILABLE, Component.text(entity.getName()));
                    return;
                }
                location.setY(y);
                String name = entity.getName();
                EntityUtils.teleport(entity, location).whenComplete((success, error) -> {
                    if (error != null) {
                        this.plugin().logger().warn("Failed to teleport " + name + " to the highest block", error);
                    }
                    this.handleFeedback(context, error == null && success ? MessageConstants.COMMAND_TOP_BLOCK_SUCCESS : MessageConstants.COMMAND_TELEPORT_FAILURE, Component.text(name));
                });
            }, () -> {}, entity);
        }
    }

    @Override
    public String getFeatureID() {
        return "top-block";
    }
}
