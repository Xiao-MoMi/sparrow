package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class DistanceCommand extends BukkitCommandFeature {

    public DistanceCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.senderType(Player.class)
                .flag(manager.flagBuilder("max-distance").withAliases("m").withComponent(IntegerParser.integerParser(0, 512)).build())
                .flag(manager.flagBuilder("disable-marker").build())
                .handler(this::execute);
    }

    private void execute(CommandContext<Player> context) {
        Player player = context.sender();
        int maxDistance = context.flags().getValue("max-distance", 256);
        this.plugin().scheduler().platform().run(() -> {
            Block block = player.getTargetBlockExact(maxDistance);
            if (block == null) {
                this.handleFeedback(context, MessageConstants.COMMAND_DISTANCE_FAILED, Component.text(maxDistance));
                return;
            }
            if (!context.flags().hasFlag("disable-marker")) {
                this.plugin().playerManager().getPlayer(player).sendDebugMarker(block.getX(), block.getY(), block.getZ());
            }
            Location source = player.getLocation();
            double dx = block.getX() + 0.5 - source.getX();
            double dy = block.getY() + 0.5 - source.getY();
            double dz = block.getZ() + 0.5 - source.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
            this.handleFeedback(context, MessageConstants.COMMAND_DISTANCE_SUCCESS,
                    Component.text(String.format(Locale.ROOT, "%.2f", distance)),
                    Component.text(String.format(Locale.ROOT, "%.2f", manhattan)));
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "distance";
    }
}
