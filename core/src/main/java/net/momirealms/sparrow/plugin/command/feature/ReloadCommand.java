package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.FlagKeys;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;

public final class ReloadCommand extends BukkitCommandFeature {

    public ReloadCommand(CommandManager commandManager, SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .flag(FlagKeys.SILENT_FLAG)
                .handler(context -> this.plugin().scheduler().platform().execute(() -> {
                    if (this.plugin().isReloading()) {
                        this.handleFeedback(context, MessageConstants.COMMAND_RELOAD_TOO_FAST);
                        return;
                    }
                    this.plugin().reloadPlugin(this.plugin().scheduler().async(), r -> this.plugin().scheduler().platform().run(r)).thenAcceptAsync(reloadResult -> {
                        if (!reloadResult.success()) {
                            this.handleFeedback(context, MessageConstants.COMMAND_RELOAD_CONFIG_FAILURE);
                            return;
                        }
                        this.handleFeedback(context, MessageConstants.COMMAND_RELOAD_CONFIG_SUCCESS,
                                Component.text(reloadResult.asyncTime() + reloadResult.syncTime()),
                                Component.text(reloadResult.asyncTime()),
                                Component.text(reloadResult.syncTime())
                        );
                    }, this.plugin().scheduler().async());
                }));
    }

    @Override
    public String getFeatureID() {
        return "reload";
    }
}
