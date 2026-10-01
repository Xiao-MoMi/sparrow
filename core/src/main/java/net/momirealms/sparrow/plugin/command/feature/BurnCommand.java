package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TimeParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultipleEntitySelector;
import org.incendo.cloud.bukkit.parser.selector.MultipleEntitySelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;

public final class BurnCommand extends BukkitCommandFeature {
    public BurnCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> command = builder.required("time", TimeParser.timeParser());
        manager.command(command.required("targets", MultipleEntitySelectorParser.multipleEntitySelectorParser())
                .permission(this.otherPermission(command))
                .handler(this::execute));
        manager.command(command.handler(this::execute));
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
        int ticks = context.get("time");
        for (Entity entity : entities) {
            this.plugin().scheduler().platform().run(() -> {
                entity.setFireTicks(ticks);
                this.handleFeedback(context, (entity == context.sender() ? MessageConstants.COMMAND_BURN_SUCCESS_SELF : MessageConstants.COMMAND_BURN_SUCCESS), Component.text(entity.getName()), Component.text(ticks));
            }, () -> {}, entity);
        }
    }

    @Override
    public String getFeatureID() {
        return "burn";
    }
}
