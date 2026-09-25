package net.momirealms.sparrow.player;

import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;
import net.momirealms.sparrow.advancement.AdvancementFrame;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

public interface SparrowPlayer {

    @NotNull
    UUID uniqueId();

    @NotNull
    String name();

    @NotNull
    ServerPlayer nmsPlayer();

    /**
     * 返回当前选中槽位持有的原版物品, 修改会直接作用于背包;
     *
     * @return 主手原物品, 空手时为原版空物品
     */
    @NotNull
    net.minecraft.world.item.ItemStack getItemInMainHand();

    /**
     * 返回玩家的网络连接, 用于直接收发数据包.
     *
     * @return 本次登录的连接
     */
    @NotNull
    PlayerConnection connection();

    /**
     * 读取客户端当前语言.
     *
     * @return 根据客户端语言标识解析的 Locale
     */
    @NotNull
    Locale locale();

    /**
     * 检查玩家当前是否拥有指定权限.
     *
     * @param permission 待检查的权限节点
     * @return 平台权限系统的检查结果
     */
    boolean hasPermission(@NotNull String permission);

    /**
     * 从眼睛下方朝视线方向抛出物品, 拾取延迟为 0, 不扣除背包物品.
     */
    void dropItem(@NotNull ItemStack stack);

    /**
     * 向聊天栏发送系统消息, 调用约定同 {@link #sendMessage(Component, boolean)}.
     *
     * @param message 待发送的文本组件
     */
    default void sendMessage(@NotNull Component message) {
        this.sendMessage(message, false);
    }

    /**
     * 发送保留样式、点击和悬浮内容的系统消息.
     *
     * @param message 待发送的文本组件
     * @param overlay true 显示在快捷栏上方, false 显示在聊天栏
     */
    void sendMessage(@NotNull Component message, boolean overlay);

    /**
     * 在快捷栏上方显示消息, 调用约定同 {@link #sendMessage(Component, boolean)}.
     *
     * @param message 待显示的文本组件
     */
    default void sendActionBar(@NotNull Component message) {
        this.sendMessage(message, true);
    }

    /**
     * 发送主副标题及以 tick 计的显示时间, 空组件会清除对应旧文本.
     */
    void sendTitle(@NotNull Component title, @NotNull Component subtitle, int fadeIn, int stay, int fadeOut);

    /**
     * 使用指定图腾播放客户端动画, 不消耗物品或写入服务器背包.
     */
    void sendTotemAnimation(@NotNull ItemStack totem);

    /**
     * 发送指定图标和样式的进度提示, 随后清除客户端临时进度记录. 图标必须为非空物品.
     */
    void sendToast(@NotNull Component text, @NotNull ItemStack icon, @NotNull AdvancementFrame frame);

    /**
     * 显示演示版介绍界面, 不改变服务器游戏模式.
     */
    void sendDemo();

    /**
     * 显示终末之诗与制作人员界面, 不修改通关记录.
     */
    void sendCredits();

    /**
     * 在当前客户端世界的指定方块显示原版调试标记, 样式和到期时间由客户端决定.
     */
    void sendDebugMarker(int x, int y, int z);
}
