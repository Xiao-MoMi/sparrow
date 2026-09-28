package net.momirealms.sparrow.plugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.util.TriConsumer;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface CommandManager {

    /**
     * 检查当前平台的命令补全是否可以异步进行
     */
    boolean asynchronousCompletion();

    /**
     * 按 commands.yml 注册一个命令. 配置中关闭或当前环境不可用的命令直接跳过.
     * <strong>只能在插件启用阶段调用</strong>.
     *
     * @param feature 待注册的命令实现
     * @param requirement 配置权限之外还要满足的条件, 例如 {@link FeaturePermission}, 为 null 时只检查配置权限
     */
    void registerFeature(@NotNull CommandFeature feature, @Nullable Permission requirement);

    /**
     * 注册默认的命令功能.
     */
    void registerDefaultFeatures();

    /**
     * 按 ID 获取已注册的命令.
     *
     * @param id 命令 ID
     * @return 已注册的命令, 未注册或在配置中关闭时为 null
     */
    @Nullable
    CommandFeature feature(@NotNull String id);

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
