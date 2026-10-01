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
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class WorkbenchCommand extends BukkitCommandFeature {
    public WorkbenchCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", PlayerParser.playerParser())
                .permission(this.otherPermission(builder))
                .handler(this::execute));
        manager.command(builder.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        this.plugin().scheduler().platform().run(() -> {
            player.openWorkbench(null, true);
            this.handleFeedback(context, (player == context.sender() ? MessageConstants.COMMAND_WORKBENCH_SUCCESS_SELF : MessageConstants.COMMAND_WORKBENCH_SUCCESS), Component.text(player.getName()));
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "workbench";
    }
}

