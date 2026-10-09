package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class ForceSayCommand extends BukkitCommandFeature {

    public ForceSayCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .required("text", StringParser.greedyStringParser())
                .handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        MultiplePlayerSelector selector = context.get("targets");
        Collection<Player> players = selector.values();
        if (players.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        String text = context.get("text");
        if (text.isBlank()) {
            this.handleFeedback(context, MessageConstants.COMMAND_FORCE_SAY_EMPTY);
            return;
        }
        if (text.stripLeading().startsWith("/")) {
            this.handleFeedback(context, MessageConstants.COMMAND_FORCE_SAY_COMMAND);
            return;
        }
        for (Player player : players) {
            this.plugin().scheduler().platform().run(() -> {
                player.chat(text);
                this.handleFeedback(context, MessageConstants.COMMAND_FORCE_SAY_SUCCESS, Component.text(player.getName()));
            }, () -> {}, player);
        }
    }

    @Override
    public String getFeatureID() {
        return "force-say";
    }
}