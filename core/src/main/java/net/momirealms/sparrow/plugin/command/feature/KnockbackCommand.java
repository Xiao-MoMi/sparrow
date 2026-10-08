package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class KnockbackCommand extends BukkitCommandFeature {

    public KnockbackCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> command = builder
                .required("x", DoubleParser.doubleParser())
                .required("y", DoubleParser.doubleParser())
                .required("z", DoubleParser.doubleParser());
        manager.command(command.required("player", PlayerParser.playerParser())
                .permission(this.otherPermission(command))
                .handler(this::execute));
        manager.command(command.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        double x = context.get("x");
        double y = context.get("y");
        double z = context.get("z");
        this.plugin().scheduler().platform().run(() -> {
                    // 和原版受击击退一样叠加到服务端记录的速度上, 客户端收到后以结果替换当前速度
                    player.setVelocity(player.getVelocity().add(new Vector(x, y, z)));
                    this.handleFeedback(
                            context,
                            (player == context.sender() ? MessageConstants.COMMAND_KNOCKBACK_SUCCESS_SELF : MessageConstants.COMMAND_KNOCKBACK_SUCCESS),
                            Component.text(player.getName()),
                            Component.text(x),
                            Component.text(y),
                            Component.text(z)
                    );
                }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "knockback";
    }
}
