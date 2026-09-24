package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultipleEntitySelector;
import org.incendo.cloud.bukkit.parser.selector.MultipleEntitySelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class ExtinguishCommand extends BukkitCommandFeature {
    public ExtinguishCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.optional("targets", MultipleEntitySelectorParser.multipleEntitySelectorParser())
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        MultipleEntitySelector selector = context.getOrDefault("targets", null);
        Collection<Entity> entities;
        if (selector != null) {
            entities = selector.values();
        } else if (context.sender() instanceof Player player) {
            entities = List.of(player);
        } else {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        if (entities.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        for (Entity entity : entities) {
            this.plugin().scheduler().platform().run(() -> {
                entity.setFireTicks(0);
                this.handleFeedback(context, MessageConstants.COMMAND_EXTINGUISH_SUCCESS, Component.text(entity.getName()));
            }, () -> {}, entity);
        }
    }

    @Override
    public String getFeatureID() {
        return "extinguish";
    }
}
