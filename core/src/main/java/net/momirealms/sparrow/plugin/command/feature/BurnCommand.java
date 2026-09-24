package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TimeParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultipleEntitySelector;
import org.incendo.cloud.bukkit.parser.selector.MultipleEntitySelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class BurnCommand extends BukkitCommandFeature {
    public BurnCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.required("targets", MultipleEntitySelectorParser.multipleEntitySelectorParser())
                .required("time", TimeParser.timeParser())
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        MultipleEntitySelector selector = context.get("targets");
        Collection<Entity> entities = selector.values();
        if (entities.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        int ticks = context.get("time");
        for (Entity entity : entities) {
            this.plugin().scheduler().platform().run(() -> {
                entity.setFireTicks(ticks);
                this.handleFeedback(context, MessageConstants.COMMAND_BURN_SUCCESS, Component.text(entity.getName()), Component.text(ticks));
            }, () -> {}, entity);
        }
    }

    @Override
    public String getFeatureID() {
        return "burn";
    }
}
