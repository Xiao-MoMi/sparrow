package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

public final class ActionBarCommand extends BukkitCommandFeature {

    public ActionBarCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .required("message", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .flag(manager.flagBuilder("legacy-color").withAliases("l"))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        MultiplePlayerSelector selector = context.get("targets");
        Collection<Player> players = selector.values();
        if (players.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }

        String message = context.get("message");
        PluginConfig.TextOptions text = PluginConfig.text();
        boolean legacy = text.parseLegacyColor() || context.flags().hasFlag("legacy-color");
        for (Player player : players) {
            BukkitSparrowPlayer receiver = this.plugin().playerManager().getPlayer(player);
            Component component = AdventureHelper.miniMessage(message, legacy, player);
            receiver.sendActionBar(component);
            this.handleFeedback(
                    context,
                    (player == context.sender() ? MessageConstants.COMMAND_ACTIONBAR_SUCCESS_SELF : MessageConstants.COMMAND_ACTIONBAR_SUCCESS),
                    Component.text(player.getName())
            );
        }
    }

    @Override
    public String getFeatureID() {
        return "actionbar";
    }
}
