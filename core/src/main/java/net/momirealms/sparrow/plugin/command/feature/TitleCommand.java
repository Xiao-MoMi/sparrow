package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.SendTitleMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class TitleCommand extends BukkitCommandFeature {

    public TitleCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("targets", GlobalPlayerParser.clusterPlayersParser())
                .required("fadeIn", IntegerParser.integerParser(0))
                .required("stay", IntegerParser.integerParser(0))
                .required("fadeOut", IntegerParser.integerParser(0))
                .required("message", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String message = context.get("message");
        String[] parts = message.split("\\\\n", -1);
        if (parts.length > 2) {
            this.handleFeedback(context, MessageConstants.COMMAND_TITLE_FORMAT);
            return;
        }
        String title = parts[0];
        String subtitle = parts.length == 2 ? parts[1] : "";
        int fadeIn = context.get("fadeIn");
        int stay = context.get("stay");
        int fadeOut = context.get("fadeOut");
        List<String> targets = context.get("targets");
        for (String input : targets) {
            OfflineSparrowPlayer target = this.resolveOnlinePlayer(context, input);
            if (target == null) continue;
            if (!(target instanceof BukkitSparrowPlayer local)) {
                this.sendRemoteRequest(context, target, new SendTitleMessage(target.uniqueId(), title, subtitle, fadeIn, stay, fadeOut));
                continue;
            }
            if (!local.isOnlineLocally()) {
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_OFFLINE, Component.text(local.name()));
                continue;
            }
            local.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            this.handleFeedback(
                    context,
                    local.platformPlayer == context.sender() ? MessageConstants.COMMAND_TITLE_SUCCESS_SELF : MessageConstants.COMMAND_TITLE_SUCCESS,
                    Component.text(local.name())
            );
        }
    }

    @Override
    public String getFeatureID() {
        return "title";
    }
}