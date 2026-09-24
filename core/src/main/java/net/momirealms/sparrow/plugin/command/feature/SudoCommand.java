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

public final class SudoCommand extends BukkitCommandFeature {
    public SudoCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.required("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .required("command", StringParser.greedyStringParser())
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        MultiplePlayerSelector selector = context.get("targets");
        Collection<Player> entities = selector.values();
        if (entities.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        String input = context.get("command");
        String command = input.startsWith("/") ? input.substring(1) : input;
        if (command.isBlank()) {
            this.handleFeedback(context, MessageConstants.COMMAND_SUDO_EMPTY);
            return;
        }
        for (Player entity : entities) {
            this.plugin().scheduler().platform().run(() -> {
                boolean executed = entity.performCommand(command);
                this.handleFeedback(context, executed ? MessageConstants.COMMAND_SUDO_SUCCESS : MessageConstants.COMMAND_SUDO_FAILURE, Component.text(entity.getName()));
            }, () -> {}, entity);
        }
    }

    @Override
    public String getFeatureID() {
        return "sudo";
    }
}
