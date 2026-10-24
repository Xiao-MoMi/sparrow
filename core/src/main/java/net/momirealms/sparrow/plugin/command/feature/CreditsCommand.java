package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.SendCreditsMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

public final class CreditsCommand extends BukkitCommandFeature {

    public CreditsCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.optional("player", GlobalPlayerParser.clusterPlayerParser())
                .handler(this::execute));
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
            this.sendRemoteRequest(context, target, new SendCreditsMessage(target.uniqueId()));
            return;
        }
        local.sendCredits();
        this.handleFeedback(
                context,
                local.platformPlayer == context.sender() ? MessageConstants.COMMAND_CREDITS_SUCCESS_SELF : MessageConstants.COMMAND_CREDITS_SUCCESS,
                Component.text(target.name())
        );
    }

    @Override
    public String getFeatureID() {
        return "credits";
    }
}