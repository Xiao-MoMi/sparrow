package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.PlayerStateMessage.Action;
import net.momirealms.sparrow.player.message.PlayerStateMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.FloatParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class WalkSpeedCommand extends BukkitCommandFeature {

    public WalkSpeedCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> command = builder
                .required("speed", FloatParser.floatParser(-1, 1));
        manager.command(command.required("player", GlobalPlayerParser.clusterPlayerParser())
                .permission(this.otherPermission(command))
                .handler(this::execute));
        manager.command(command.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String input = context.getOrDefault("player", context.sender() instanceof Player sender ? sender.getUniqueId().toString() : null);
        if (input == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        OfflineSparrowPlayer target = this.resolveOnlinePlayer(context, input);
        if (target == null) return;
        if (!(target instanceof BukkitSparrowPlayer local)) {
            this.sendRemoteRequest(context, target, new PlayerStateMessage(target.uniqueId(), Action.WALK_SPEED, context.<Float>get("speed")));
            return;
        }
        Player player = local.platformPlayer;
        float speed = context.get("speed");
        this.plugin().scheduler().platform().run(() -> {
                    player.setWalkSpeed(speed);
                    this.handleFeedback(
                            context,
                            (player == context.sender() ? MessageConstants.COMMAND_WALK_SPEED_SUCCESS_SELF : MessageConstants.COMMAND_WALK_SPEED_SUCCESS),
                            Component.text(player.getName()),
                            Component.text(speed)
                    );
                }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "walk_speed";
    }
}