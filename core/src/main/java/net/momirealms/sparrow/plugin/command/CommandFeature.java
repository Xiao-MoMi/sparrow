package net.momirealms.sparrow.plugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.plugin.Plugin;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;

public interface CommandFeature {

    Command<CommandSender> registerCommand(org.incendo.cloud.CommandManager<CommandSender> cloudCommandManager, Command.Builder<CommandSender> builder);

    String getFeatureID();

    /**
     * 注册与命令功能相关的附加能力.
     * 例如注册 suggestion, parser, listener 或其他需要与命令生命周期绑定的资源.
     */
    void registerRelatedFunctions();

    void unregisterRelatedFunctions();

    /**
     * 基于 Cloud 命令上下文发送一条消息.
     *
     * @param context Cloud 命令上下文
     * @param key 可翻译组件构建器, 表示反馈消息的翻译键
     */
    void handleFeedback(CommandContext<?> context, TranslatableComponent.Builder key, Component... args);

    /**
     * 向指定命令发送者发送消息.
     *
     * @param sender 接收反馈的命令发送者对象
     * @param key 可翻译组件构建器, 表示反馈消息的翻译键
     * @param args 写入可翻译组件的参数列表
     */
    void handleFeedback(CommandSender sender, TranslatableComponent.Builder key, Component... args);

    CommandManager commandManager();

    CommandConfig commandConfig();

    Plugin plugin();

    /**
     * 设定命令是否可用.
     */
    default boolean isAvailable() {
        return true;
    }
}
