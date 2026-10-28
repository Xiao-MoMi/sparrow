package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.SinglePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.SinglePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class SudoCommand extends BukkitCommandFeature {

    public SudoCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", SinglePlayerSelectorParser.singlePlayerSelectorParser())
                .required("command", StringParser.greedyStringParser())
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.<SinglePlayerSelector>get("player").single();
        String input = context.get("command");
        String command = input.startsWith("/") ? input.substring(1) : input;
        if (command.isBlank()) {
            this.handleFeedback(context, MessageConstants.COMMAND_SUDO_EMPTY);
            return;
        }
        this.plugin().scheduler().platform().run(() -> {
            boolean executed = player.performCommand(command);
            this.handleFeedback(
                    context,
                    executed
                            ? (player == context.sender() ? MessageConstants.COMMAND_SUDO_SUCCESS_SELF : MessageConstants.COMMAND_SUDO_SUCCESS)
                            : (player == context.sender() ? MessageConstants.COMMAND_SUDO_FAILURE_SELF : MessageConstants.COMMAND_SUDO_FAILURE),
                    Component.text(player.getName())
            );
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "sudo";
    }
}