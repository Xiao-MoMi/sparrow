package net.momirealms.sparrow.plugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.util.Pair;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.incendo.cloud.bukkit.data.Selector;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

public abstract class BukkitCommandFeature extends AbstractCommandFeature {

    public BukkitCommandFeature(CommandManager commandManager, SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    /**
     * 根据 Cloud Selector 解析实体选择结果, 并返回对应的翻译键与参数组件.
     * 当选择到 1 个实体时返回 single, 参数为实体名称. 当选择到多个实体时返回 multiple, 参数为实体数量.
     *
     * @param selector Cloud 选择器, 用于获取实体集合
     * @param single 单个实体时使用的翻译组件
     * @param multiple 多个实体时使用的翻译组件
     * @return left 为翻译组件, right 为用于填充翻译参数的组件
     */
    public Pair<TranslatableComponent, Component> resolveSelector(Selector<? extends Entity> selector, TranslatableComponent single, TranslatableComponent multiple) {
        Collection<? extends Entity> entities = selector.values();
        if (entities.size() == 1) {
            return Pair.of(single, Component.text(entities.iterator().next().getName()));
        } else {
            return Pair.of(multiple, Component.text(entities.size()));
        }
    }

    /**
     * 根据实体集合解析选择结果, 并返回对应的翻译键与参数组件.
     * 当集合大小为 1 时返回 single, 参数为实体名称. 当集合大小大于 1 时返回 multiple, 参数为实体数量.
     *
     * @param selector 实体集合
     * @param single 单个实体时使用的翻译组件
     * @param multiple 多个实体时使用的翻译组件
     * @return left 为翻译组件, right 为用于填充翻译参数的组件
     */
    public Pair<TranslatableComponent, Component> resolveSelector(Collection<? extends Entity> selector, TranslatableComponent single, TranslatableComponent multiple) {
        if (selector.size() == 1) {
            return Pair.of(single, Component.text(selector.iterator().next().getName()));
        } else {
            return Pair.of(multiple, Component.text(selector.size()));
        }
    }

    /**
     * 按名字或 UUID 查找集群在线玩家, 未找到时发送默认离线提示.
     *
     * @return 本服或远程在线玩家, 未找到时返回 null
     */
    @Nullable
    protected OfflineSparrowPlayer resolveOnlinePlayer(@NotNull CommandContext<CommandSender> context, @NotNull String input) {
        return this.resolveOnlinePlayer(context, input, MessageConstants.COMMAND_PLAYER_OFFLINE);
    }

    /**
     * 查找集群在线玩家, 使用调用方指定的离线提示.
     *
     * @param input 玩家名字或 UUID, 省略目标的规则由命令处理
     * @param offline 未找到玩家时的提示, 参数 0 为原始输入
     * @return 本服玩家返回 BukkitSparrowPlayer, 远程玩家返回 OfflineSparrowPlayer, 未找到时返回 null
     */
    @Nullable
    protected OfflineSparrowPlayer resolveOnlinePlayer(
            @NotNull CommandContext<CommandSender> context,
            @NotNull String input,
            @NotNull TranslatableComponent offline
    ) {
        UUID uuid = UUIDUtils.parse(input);
        OfflineSparrowPlayer target = uuid == null
                ? this.plugin().playerManager().getGlobalOnlinePlayer(input)
                : this.plugin().playerManager().getGlobalOnlinePlayer(uuid);
        if (target == null) {
            this.handleFeedback(context, offline, Component.text(input));
        }
        return target;
    }

    /**
     * 向目标玩家所在服务器提交单向请求, 使用默认的离线和已发送提示.
     * 已发送表示请求已提交, 目标服的业务执行结果由单向消息语义决定.
     */
    protected void sendRemoteRequest(@NotNull CommandContext<CommandSender> context, @NotNull OfflineSparrowPlayer target, @NotNull OneWayMessage<FriendlyByteBuf> message) {
        this.sendRemoteRequest(
                context,
                target,
                message,
                MessageConstants.COMMAND_PLAYER_OFFLINE,
                MessageConstants.COMMAND_PLAYER_REQUEST_SENT
        );
    }

    /**
     * 提交单向请求, 使用调用方指定的离线和已发送提示.
     *
     * @param target 目标玩家, 调用方负责区分本服执行和远程发送
     * @param message 本次发送的消息, 目标服务器由玩家当前所在服务器确定
     * @param offline 无法取得目标服务器时的提示, 参数 0 为玩家名
     * @param sent 请求提交后的提示, 参数 0 为玩家名
     */
    protected void sendRemoteRequest(
            @NotNull CommandContext<CommandSender> context,
            @NotNull OfflineSparrowPlayer target,
            @NotNull OneWayMessage<FriendlyByteBuf> message,
            @NotNull TranslatableComponent offline,
            @NotNull TranslatableComponent sent
    ) {
        String server = target.getCurrentServerId();
        if (server == null) {
            this.handleFeedback(context, offline, Component.text(target.name()));
            return;
        }
        this.plugin().messageBrokerManager().publishOneWay(message, server);
        this.handleFeedback(context, sent, Component.text(target.name()));
    }

    @Override
    public SparrowPlugin plugin() {
        return (SparrowPlugin) super.plugin();
    }
}