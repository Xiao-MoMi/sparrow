package net.momirealms.sparrow.player;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.util.Util;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.proxy.minecraft.server.network.ServerCommonPacketListenerImplProxy;
import net.momirealms.sparrow.ui.SparrowUI;
import net.momirealms.sparrow.ui.Subscription;
import net.momirealms.sparrow.ui.network.NMSPacketEvent;
import net.momirealms.sparrow.ui.network.NetworkUser;
import net.momirealms.sparrow.ui.network.packet.ConnectionState;
import net.momirealms.sparrow.ui.network.packet.PacketFlow;
import net.momirealms.sparrow.ui.network.packet.PacketType;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.craftbukkit.util.CraftChatMessage;

import java.util.concurrent.TimeUnit;

// 登录过程中的两道门: login_finished 等名单登记, finish_configuration 等玩家锁和初始化任务
final class LoginPacketGate {
    private static final long TIMEOUT_SECONDS = 30;

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private Subscription loginFinished;
    private Subscription finishConfiguration;

    void onEnable() {
        this.loginFinished = SparrowUI.getInstance().networkManager().listenNMS(new PacketType("minecraft:login_finished", ConnectionState.LOGIN, PacketFlow.CLIENTBOUND), this::onLoginFinished);
        this.finishConfiguration = SparrowUI.getInstance().networkManager().listenNMS(new PacketType("minecraft:finish_configuration", ConnectionState.CONFIGURATION, PacketFlow.CLIENTBOUND), this::onFinishConfiguration);
    }

    // proxy 收到 login_finished 才断开旧服, 名单登记写入 Redis 后再放行, 切服时新服的登记因此先于旧服的 Quit
    private void onLoginFinished(NetworkUser user, NMSPacketEvent event, Object packet) {
        Channel channel = user.channel();
        PlayerManager manager = this.plugin.playerManager();
        PlayerConnection connection = manager.getConnection(channel);
        // 连接已关闭时登记已被移除, 写出会自行失败
        if (connection == null) return;
        event.cancel();
        manager.register(connection).whenComplete((ignored, error) -> {
            if (error != null) {
                this.plugin.logger().warn("Failed to register " + connection.name() + " in player registry before login", error);
            }
            channel.eventLoop().execute(() -> {
                if (channel.isActive() && !this.loginFinished.isClosed()) {
                    user.sendPacketSilently(packet);
                }
            });
        });
    }

    // 等玩家锁和初始化任务完成后补发
    private void onFinishConfiguration(NetworkUser user, NMSPacketEvent event, Object packet) {
        Channel channel = user.channel();
        PlayerManager manager = this.plugin.playerManager();
        PlayerConnection connection = manager.getConnection(channel);
        if (connection == null) {
            event.cancel();
            return;
        }
        // 已进服的玩家重新进入配置阶段时直接放行
        if (connection.prepared()) return;
        event.cancel();
        ServerConfigurationPacketListenerImpl listener = (ServerConfigurationPacketListenerImpl) ((Connection) connection.handle()).getPacketListener();
        // 原版发出终止包时已开始 15 秒应答计时. 扣包期间恢复保活, 补发时重新计时
        ServerCommonPacketListenerImplProxy.INSTANCE.setClosed(listener, false);
        // 超时只结束门禁等待, 断线收尾仍等准备真正结束
        manager.prepare(connection, System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS))
                .copy()
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((ignored, failure) -> {
                    if (!channel.isActive()) return;
                    if (failure != null) {
                        this.plugin.logger().warn("Failed to prepare player " + connection.name(), failure);
                        // 非主线程断开会阻塞等待主线程, 交给主线程执行
                        if (!this.plugin.javaPlugin().isEnabled()) return;
                        this.plugin.scheduler().platform().run(() -> listener.disconnect(
                                CraftChatMessage.fromJSON(AdventureHelper.componentToJson(this.plugin.translationManager().render(MessageConstants.PLAYER_DATA_NOT_READY)))
                        ));
                        return;
                    }
                    channel.eventLoop().execute(() -> {
                        // 网络管理器在 PluginDisableEvent 中关闭, 此时 Channel 仍可能有效
                        if (!channel.isActive() || this.finishConfiguration.isClosed()) return;
                        ServerCommonPacketListenerImplProxy.INSTANCE.setClosedListenerTime(listener, Util.getMillis());
                        ServerCommonPacketListenerImplProxy.INSTANCE.setClosed(listener, true);
                        user.sendPacketSilently(packet);
                    });
                });
    }
}