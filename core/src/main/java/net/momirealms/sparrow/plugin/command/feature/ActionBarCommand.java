package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.SendMessageMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BatchCommandFeedback;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class ActionBarCommand extends BukkitCommandFeature {

    public ActionBarCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("targets", GlobalPlayerParser.clusterPlayersParser())
                .required("message", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String message = context.get("message");
        List<String> targets = context.get("targets");
        BatchCommandFeedback feedback = new BatchCommandFeedback(this, context, targets.size());
        for (String input : targets) {
            OfflineSparrowPlayer target = this.resolveOnlinePlayer(context, input, feedback);
            if (target == null) continue;
            if (!(target instanceof BukkitSparrowPlayer local)) {
                this.sendRemoteRequest(context, target, new SendMessageMessage(target.uniqueId(), message, true), feedback);
                continue;
            }
            if (!local.isOnlineLocally()) {
                feedback.failure(MessageConstants.COMMAND_PLAYER_OFFLINE, Component.text(local.name()));
                continue;
            }
            local.sendActionBar(message);
            feedback.success(
                    local.platformPlayer == context.sender() ? MessageConstants.COMMAND_ACTIONBAR_SUCCESS_SELF : MessageConstants.COMMAND_ACTIONBAR_SUCCESS,
                    Component.text(local.name())
            );
        }
    }

    @Override
    public String getFeatureID() {
        return "actionbar";
    }
}