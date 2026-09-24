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

    /**
     * 创建 reload 命令功能实例.
     *
     * @param commandManager 命令管理器
     * @param plugin 插件实例
     */
    public ReloadCommand(CommandManager commandManager, SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    /**
     * 组装 reload 命令结构.
     *
     * @param manager Cloud 命令管理器
     * @param builder 基础命令构建器
     * @return 组装完成的命令构建器
     */
    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .flag(FlagKeys.SILENT_FLAG)
                .handler(context -> {
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
                });
    }

    /**
     * 返回内置命令配置使用的 Feature 标识.
     *
     * @return 功能标识, 固定为 "reload"
     */
    @Override
    public String getFeatureID() {
        return "reload";
    }
}
