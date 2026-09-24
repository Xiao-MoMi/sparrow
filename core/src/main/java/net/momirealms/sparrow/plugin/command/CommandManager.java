package net.momirealms.sparrow.plugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.util.Index;
import net.momirealms.sparrow.util.TriConsumer;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public interface CommandManager {
    /**
     * 使用给定命令实现与命令配置注册一个命令功能.
     *
     * @param feature 待注册的命令实现
     * @param config 功能对应的命令配置
     */
    void registerFeature(CommandFeature feature, CommandConfig config);

    void unregisterFeatures();

    /**
     * 注册默认的命令功能.
     */
    void registerDefaultFeatures();

    /**
     * 当前命令系统已声明的全部命令功能.
     */
    Index<String, CommandFeature> features();

    /**
     * 设置命令反馈的输出.
     *
     * @param feedbackConsumer (发送者, 翻译键, 消息组件)
     */
    void setFeedbackConsumer(@NotNull TriConsumer<CommandSender, String, Component> feedbackConsumer);

    /**
     * 创建一个默认的命令反馈的输出.
     */
    TriConsumer<CommandSender, String, Component> defaultFeedbackConsumer();

    /**
     * 根据 CommandConfig 构建 Cloud 命令构建器集合.
     *
     * @param config 命令配置
     * @return 构建出的命令构建器集合
     */
    Collection<Command.Builder<CommandSender>> buildCommandBuilders(CommandConfig config);

    /**
     * 构建并发送一条命令反馈.
     *
     * @param sender 命令发送者
     * @param key 翻译键
     * @param args 参数列表
     */
    void handleCommandFeedback(CommandSender sender, TranslatableComponent.Builder key, Component... args);

    /**
     * 直接发送一条命令反馈.
     *
     * @param sender 命令发送者
     * @param node 翻译键
     * @param component 消息组件
     */
    void handleCommandFeedback(CommandSender sender, String node, Component component);


    org.incendo.cloud.CommandManager<CommandSender> getCommandManager();
}
