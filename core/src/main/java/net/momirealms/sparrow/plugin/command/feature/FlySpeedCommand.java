package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.FloatParser;
import org.jetbrains.annotations.NotNull;

public final class FlySpeedCommand extends BukkitCommandFeature {
    public FlySpeedCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .required("speed", FloatParser.floatParser(-1, 1))
                .optional("player", PlayerParser.playerParser())
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        float speed = context.get("speed");
        this.plugin().scheduler().platform().run(() -> {
            player.setFlySpeed(speed);
            this.handleFeedback(context, MessageConstants.COMMAND_FLYSPEED_SUCCESS, Component.text(player.getName()), Component.text(speed));
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "flyspeed";
    }
}
