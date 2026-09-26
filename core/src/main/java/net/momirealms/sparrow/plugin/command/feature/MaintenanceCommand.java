package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.maintenance.MaintenanceFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.BooleanParser;
import org.jetbrains.annotations.NotNull;

public final class MaintenanceCommand extends BukkitCommandFeature {

    public MaintenanceCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.optional("active", BooleanParser.booleanParser())
                .handler(this::execute);
    }

    // 不带参数时查询当前状态
    private void execute(CommandContext<CommandSender> context) {
        MaintenanceFeature feature = this.plugin().featureManager().feature(MaintenanceFeature.ID, MaintenanceFeature.class);
        if (!feature.enabled()) {
            this.handleFeedback(context, MessageConstants.COMMAND_FEATURE_DISABLED, Component.text(MaintenanceFeature.ID));
            return;
        }
        Boolean active = context.getOrDefault("active", null);
        if (active == null) {
            this.handleFeedback(context, feature.active() ? MessageConstants.COMMAND_MAINTENANCE_ACTIVE : MessageConstants.COMMAND_MAINTENANCE_INACTIVE);
            return;
        }
        feature.active(active);
        this.handleFeedback(context, active ? MessageConstants.COMMAND_MAINTENANCE_ENABLED : MessageConstants.COMMAND_MAINTENANCE_DISABLED);
    }

    @Override
    public String getFeatureID() {
        return MaintenanceFeature.ID;
    }
}
