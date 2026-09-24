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
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class TitleCommand extends BukkitCommandFeature {
    public TitleCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.required("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .required("fadeIn", IntegerParser.integerParser(0))
                .required("stay", IntegerParser.integerParser(0))
                .required("fadeOut", IntegerParser.integerParser(0))
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
        String[] parts = message.split("\\\\n", -1);
        if (parts.length > 2) {
            this.handleFeedback(context, MessageConstants.COMMAND_TITLE_FORMAT);
            return;
        }
        int fadeIn = context.get("fadeIn");
        int stay = context.get("stay");
        int fadeOut = context.get("fadeOut");
        for (Player player : players) {
            SparrowPlayer receiver = this.plugin().playerManager().getPlayer(player);
            Component main = Components.miniMessage(placeholders ? this.plugin().compatibilityManager().parsePlaceholders(player, parts[0]) : parts[0], legacy);
            Component subtitle = parts.length == 2 ? Components.miniMessage(placeholders ? this.plugin().compatibilityManager().parsePlaceholders(player, parts[1]) : parts[1], legacy) : Component.empty();
            receiver.sendTitle(main, subtitle, fadeIn, stay, fadeOut);
            this.handleFeedback(context, MessageConstants.COMMAND_TITLE_SUCCESS, Component.text(player.getName()));
        }
    }

    @Override
    public String getFeatureID() {
        return "title";
    }
}
