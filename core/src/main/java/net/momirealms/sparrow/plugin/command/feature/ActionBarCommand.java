package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.Components;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class ActionBarCommand extends BukkitCommandFeature {
    public ActionBarCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.required("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .required("message", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .flag(manager.flagBuilder("legacy-color").withAliases("l"))
                .flag(manager.flagBuilder("parse").withAliases("p"))
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        MultiplePlayerSelector selector = context.get("targets");
        Collection<Player> players = selector.values();
        if (players.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }

        String message = context.get("message");
        boolean legacy = context.flags().hasFlag("legacy-color");
        boolean placeholders = context.flags().hasFlag("parse");
        for (Player player : players) {
            SparrowPlayer receiver = this.plugin().playerManager().getPlayer(player);
            Component component = Components.miniMessage(placeholders ? this.plugin().compatibilityManager().parsePlaceholders(player, message) : message, legacy);
            receiver.sendActionBar(component);
            this.handleFeedback(context, MessageConstants.COMMAND_ACTIONBAR_SUCCESS, Component.text(player.getName()));
        }
    }

    @Override
    public String getFeatureID() {
        return "actionbar";
    }
}
