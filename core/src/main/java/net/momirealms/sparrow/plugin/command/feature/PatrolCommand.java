package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.patrol.PatrolFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.EntityUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class PatrolCommand extends BukkitCommandFeature {
    public PatrolCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.senderType(Player.class)
                .optional("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .handler(this::execute);
    }

    private void execute(CommandContext<Player> context) {
        PatrolFeature patrol = this.plugin().featureManager().feature(PatrolFeature.ID, PatrolFeature.class);
        if (!patrol.enabled()) {
            this.handleFeedback(context, MessageConstants.COMMAND_FEATURE_DISABLED, Component.text(PatrolFeature.ID));
            return;
        }
        Player patroller = context.sender();
        MultiplePlayerSelector selector = context.getOrDefault("targets", null);
        Collection<? extends Player> candidates = selector != null ? selector.values() : Bukkit.getOnlinePlayers();
        Player target = patrol.claimNext(patroller, candidates);
        if (target == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PATROL_EMPTY);
            return;
        }
        // 传送到目标位置
        String name = target.getName();
        Location destination = target.getLocation();
        EntityUtils.teleport(patroller, destination).whenComplete((success, error) -> {
            if (error != null) {
                this.plugin().logger().warn("Failed to teleport " + patroller.getName() + " to " + name, error);
            }
            this.handleFeedback(context, error == null && success ? MessageConstants.COMMAND_PATROL_SUCCESS : MessageConstants.COMMAND_TELEPORT_FAILURE, Component.text(name));
        });
    }

    @Override
    public String getFeatureID() {
        return "patrol";
    }
}
