package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultipleEntitySelector;
import org.incendo.cloud.bukkit.parser.selector.MultipleEntitySelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class SmiteCommand extends BukkitCommandFeature {

    public SmiteCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("targets", MultipleEntitySelectorParser.multipleEntitySelectorParser())
                .flag(manager.flagBuilder("effect"))
                .handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        MultipleEntitySelector selector = context.get("targets");
        Collection<Entity> entities = selector.values();
        if (entities.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        boolean effect = context.flags().hasFlag("effect");
        for (Entity entity : entities) {
            this.plugin().scheduler().platform().run(() -> {
                Location location = entity.getLocation();
                if (effect) {
                    location.getWorld().strikeLightningEffect(location);
                } else {
                    location.getWorld().strikeLightning(location);
                }
                this.handleFeedback(
                        context,
                        effect ? MessageConstants.COMMAND_SMITE_EFFECT : MessageConstants.COMMAND_SMITE_SUCCESS,
                        Component.text(entity.getName())
                );
            }, () -> {}, entity);
        }
    }

    @Override
    public String getFeatureID() {
        return "smite";
    }
}