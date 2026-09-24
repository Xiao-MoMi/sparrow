package net.momirealms.sparrow.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import net.momirealms.sparrow.proxy.minecraft.network.ConnectionProxy;
import net.momirealms.sparrow.proxy.minecraft.network.ProtocolSwapHandlerProxy;
import net.momirealms.sparrow.proxy.minecraft.network.protocol.PacketProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.channels.ClosedChannelException;
import java.util.UUID;

public abstract class NetworkUser {
    private final UUID uniqueId;
    private final String name;
    private final ChannelHandler connection;
    private final Channel channel;

    protected NetworkUser(@NotNull ChannelHandler connection, @NotNull UUID uniqueId, @NotNull String name) {
        this.connection = connection;
        this.channel = (Channel) ConnectionProxy.INSTANCE.getChannel(connection);
        this.uniqueId = uniqueId;
        this.name = name;
    }

    /**
     * 返回本次登录的玩家 UUID.
     *
     * @return 登录身份对应的 UUID
     */
    @NotNull
    public UUID uniqueId() {
        return this.uniqueId;
    }

    /**
     * 返回创建时记录的玩家名.
     *
     * @return 本次登录的玩家名
     */
    @NotNull
    public String name() {
        return this.name;
    }

    /**
     * 返回创建时保存的原版网络 Connection, 以 Netty 的处理器类型暴露.
     * 退出后仍保留同一引用, 连接是否可用取决于其当前状态.
     *
     * @return 本次连接的原版网络对象
     */
    @NotNull
    public ChannelHandler connection() {
        return this.connection;
    }

    /**
     * 返回本次连接使用的 Netty Channel, 可在 Join 初始化前取得.
     * 退出后仍保留同一引用, Channel 可能已经关闭.
     *
     * @return 原版网络连接对应的 Channel
     */
    @NotNull
    public Channel nettyChannel() {
        return this.channel;
    }

    /**
     * 向客户端发送原版 NMS 数据包, 经过连接现有的出站处理器.
     * 可在任意线程调用, 实际发送在连接的 event loop 执行; 返回不表示客户端已收到.
     * 平台玩家初始化前也可调用, <strong>包的方向和协议阶段必须与连接当前状态匹配</strong>.
     *
     * @param packet 当前服务端版本的客户端方向 NMS Packet, 调用后交由连接处理
     * @throws IllegalArgumentException 参数不是 NMS Packet
     */
    public void sendPacket(@NotNull Object packet) {
        if (!PacketProxy.CLASS.isInstance(packet)) {
            throw new IllegalArgumentException("Expected an NMS packet");
        }
        this.transfer(packet, true, false);
    }

    /**
     * 模拟客户端发送原版 NMS 数据包, 从原版解码器之后进入入站处理链.
     * 可在任意线程调用, 实际注入在连接的 event loop 执行, 后续由原版监听器安排处理线程.
     * 协议切换包会先执行原版的停读和解码器切换准备; 返回不表示服务器已处理完成.
     *
     * @param packet 当前服务端版本的服务端方向 NMS Packet, <strong>必须匹配连接当前协议阶段</strong>
     * @throws IllegalArgumentException 参数不是 NMS Packet
     */
    public void receivePacket(@NotNull Object packet) {
        if (!PacketProxy.CLASS.isInstance(packet)) {
            throw new IllegalArgumentException("Expected an NMS packet");
        }
        this.transfer(packet, false, false);
    }

    /**
     * 向客户端发送当前服务端协议的包 ID 和 payload, 缓冲中不带长度、压缩或加密头.
     * 线程和完成语义同 {@link #sendPacket(Object)}. 需要推进原版协议状态的包应使用 NMS 入口.
     * <strong>调用后缓冲所有权交给连接, 调用方不得再修改或释放; 空帧直接释放</strong>.
     *
     * @param frame 已写入包 ID 和 payload 的可读缓冲
     */
    public void sendByteBuf(@NotNull ByteBuf frame) {
        this.transfer(frame, true, true);
    }

    /**
     * 模拟收到当前服务端协议的包 ID 和 payload, 从原版解码器之前进入入站处理链.
     * 缓冲中不带长度、压缩或加密头; 线程和完成语义同 {@link #receivePacket(Object)}.
     * <strong>调用后缓冲所有权交给连接, 调用方不得再修改或释放; 空帧直接释放</strong>.
     *
     * @param frame 与当前入站协议匹配的包 ID 和 payload 缓冲
     */
    public void receiveByteBuf(@NotNull ByteBuf frame) {
        this.transfer(frame, false, true);
    }

    // 入队后由 event loop 接管消息, 提交失败时释放尚未交出的缓冲.
    private void transfer(Object message, boolean outbound, boolean bytes) {
        if (this.channel.eventLoop().inEventLoop()) {
            this.transferOnEventLoop(message, outbound, bytes);
        } else {
            try {
                this.channel.eventLoop().execute(() -> this.transferOnEventLoop(message, outbound, bytes));
            } catch (RuntimeException failure) {
                ReferenceCountUtil.release(message);
                throw failure;
            }
        }
    }

    // 协议节点可能在排队期间被替换, 每次在实际注入时查找入口.
    private void transferOnEventLoop(Object message, boolean outbound, boolean bytes) {
        if (bytes && !((ByteBuf) message).isReadable()) {
            ReferenceCountUtil.release(message);
            return;
        }
        ChannelPipeline pipeline = this.channel.pipeline();
        ChannelHandlerContext context = null;
        try {
            if (!this.channel.isOpen()) {
                throw new ClosedChannelException();
            }
            if (!outbound) {
                context = pipeline.context("decoder");
                if (context == null) {
                    context = pipeline.context("inbound_config");
                    if (bytes || context == null) {
                        throw new IllegalStateException("Minecraft inbound protocol decoder is not installed");
                    }
                } else if (bytes) {
                    context = this.contextBefore(context.name());
                } else if (PacketProxy.INSTANCE.isTerminal(message)) {
                    // NMS 对象跳过解码, 需补上终止包的停读与 inbound_config 安装.
                    ProtocolSwapHandlerProxy.INSTANCE.handleInboundTerminalPacket(context, message);
                }
            }
        } catch (Exception | Error failure) {
            ReferenceCountUtil.release(message);
            pipeline.fireExceptionCaught(failure);
            return;
        }

        // 从此处起消息归下游所有, 下游自行处理异常和引用计数.
        if (outbound) {
            this.channel.writeAndFlush(message);
        } else if (context == null) {
            pipeline.fireChannelRead(message);
            pipeline.fireChannelReadComplete();
        } else {
            context.fireChannelRead(message);
            context.fireChannelReadComplete();
        }
    }

    @Nullable
    private ChannelHandlerContext contextBefore(String target) {
        ChannelPipeline pipeline = this.channel.pipeline();
        ChannelHandlerContext previous = null;
        for (String name : pipeline.names()) {
            if (name.equals(target)) return previous;
            previous = pipeline.context(name);
        }
        throw new IllegalStateException("Minecraft pipeline handler is not installed: " + target);
    }
}
