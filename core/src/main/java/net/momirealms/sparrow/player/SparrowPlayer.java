package net.momirealms.sparrow.player;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

public interface SparrowPlayer {

    /**
     * 返回本次登录的玩家 UUID.
     *
     * @return 登录身份对应的 UUID
     */
    @NotNull
    UUID uniqueId();

    /**
     * 返回创建时记录的玩家名.
     *
     * @return 本次登录的玩家名
     */
    @NotNull
    String name();

    /**
     * 返回创建时保存的原版网络 Connection, 以 Netty 的处理器类型暴露.
     * 退出后仍保留同一引用, 连接是否可用取决于其当前状态.
     *
     * @return 本次连接的原版网络对象
     */
    @NotNull
    ChannelHandler connection();

    /**
     * 返回本次连接使用的 Netty Channel, 可在 Join 初始化前取得.
     * 退出后仍保留同一引用, Channel 可能已经关闭.
     *
     * @return 原版网络连接对应的 Channel
     */
    @NotNull
    Channel nettyChannel();

    /**
     * 返回是否已完成 Join 初始化且尚未释放平台玩家引用.
     * 查询结果反映当前绑定状态, 后续操作仍可能遇到玩家退出.
     *
     * @return 已绑定平台玩家时为 true, 初始化前或清理后为 false
     */
    boolean initialized();

    /**
     * 读取客户端当前语言, <strong>玩家必须已经初始化且尚未退出</strong>.
     *
     * @return 根据客户端语言标识解析的 Locale
     * @throws IllegalStateException 尚未初始化或平台玩家引用已释放
     */
    @NotNull
    Locale locale();

    /**
     * 检查玩家当前是否拥有指定权限, <strong>必须在玩家所属线程调用, 且玩家已经初始化且尚未退出</strong>.
     *
     * @param permission 待检查的权限节点
     * @return 平台权限系统的检查结果
     * @throws IllegalStateException 尚未初始化或平台玩家引用已释放
     */
    boolean hasPermission(@NotNull String permission);

    /**
     * 向聊天栏发送系统消息, 调用约定同 {@link #sendMessage(Component, boolean)}.
     *
     * @param message 待发送的文本组件
     * @throws IllegalStateException 尚未初始化或平台玩家引用已释放
     */
    default void sendMessage(@NotNull Component message) {
        this.sendMessage(message, false);
    }

    /**
     * 发送保留样式、点击和悬浮内容的系统消息.
     * <strong>玩家必须已经初始化且尚未退出</strong>.
     *
     * @param message 待发送的文本组件
     * @param overlay true 显示在快捷栏上方, false 显示在聊天栏
     * @throws IllegalStateException 尚未初始化或平台玩家引用已释放
     */
    void sendMessage(@NotNull Component message, boolean overlay);

    /**
     * 在快捷栏上方显示消息, 调用约定同 {@link #sendMessage(Component, boolean)}.
     *
     * @param message 待显示的文本组件
     * @throws IllegalStateException 尚未初始化或平台玩家引用已释放
     */
    default void sendActionBar(@NotNull Component message) {
        this.sendMessage(message, true);
    }
}
