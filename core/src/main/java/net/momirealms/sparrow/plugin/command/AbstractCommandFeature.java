package net.momirealms.sparrow.plugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.plugin.Plugin;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;

public abstract class AbstractCommandFeature implements CommandFeature {
    protected final CommandManager commandManager;
    private final Plugin plugin;
    protected CommandConfig commandConfig;

    public AbstractCommandFeature(CommandManager commandManager, Plugin plugin) {
        this.commandManager = commandManager;
        this.plugin = plugin;
    }

    /**
     * 构建并向 Cloud 命令管理器注册该功能的命令.
     *
     * @param manager Cloud 命令管理器
     * @param builder 基础命令构建器
     * @return 已构建且已注册到 Cloud 的命令实例
     */
    @Override
    @SuppressWarnings("unchecked")
    public Command<CommandSender> registerCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command<CommandSender> command = (Command<CommandSender>) assembleCommand(manager, builder).build();
        manager.command(command);
        return command;
    }

    /**
     * 实现命令功能, builder 已包含根命令节点与基础权限信息, 只需实现具体功能.
     */
    public abstract Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder);

    /**
     * 注册与命令相关的附加能力.
     */
    @Override
    public void registerRelatedFunctions() {
        // empty
    }

    @Override
    public void unregisterRelatedFunctions() {
        // empty
    }

    @Override
    public void handleFeedback(CommandContext<?> context, TranslatableComponent.Builder key, Component... args) {
        if (context.flags().hasFlag("silent")) {
            return;
        }
        commandManager.handleCommandFeedback((CommandSender) context.sender(), key, args);
    }

    @Override
    public void handleFeedback(CommandSender sender, TranslatableComponent.Builder key, Component... args) {
        commandManager.handleCommandFeedback(sender, key, args);
    }

    @Override
    public CommandManager commandManager() {
        return commandManager;
    }

    @Override
    public CommandConfig commandConfig() {
        return commandConfig;
    }

    public void setCommandConfig(CommandConfig commandConfig) {
        this.commandConfig = commandConfig;
    }

    @Override
    public Plugin plugin() {
        return plugin;
    }
}
