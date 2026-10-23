package net.momirealms.sparrow.player;

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

import java.net.InetAddress;
import java.nio.channels.ClosedChannelException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerConnection {
    private final UUID uniqueId;
    private final String name;
    private final InetAddress ip;
    private final ChannelHandler handle;
    private final Channel channel;
    private final long connectedAt;
    private boolean joined;
    private volatile CompletableFuture<Void> preparation; // 取锁, 加载档案与准备监听器的整体结果, 尚未开始准备时为 null
    private volatile boolean prepared;
    private CompletableFuture<Void> ending; // 由 PlayerManager 串行发起, 覆盖退出回调到玩家锁释放

    PlayerConnection(@NotNull ChannelHandler handle, @NotNull UUID uniqueId, @NotNull String name, @Nullable InetAddress ip) {
        this.handle = handle;
        this.channel = (Channel) ConnectionProxy.INSTANCE.getChannel(handle);
        this.uniqueId = uniqueId;
        this.name = name;
        this.ip = ip;
        this.connectedAt = System.currentTimeMillis();
    }

    @NotNull
    public UUID uniqueId() {
        return this.uniqueId;
    }

    @NotNull
    public String name() {
        return this.name;
    }

    @Nullable
    public InetAddress ip() {
        return this.ip;
    }

    @NotNull
    public ChannelHandler handle() {
        return this.handle;
    }

    @NotNull
    public Channel channel() {
        return this.channel;
    }

    public long connectedAt() {
        return this.connectedAt;
    }

    boolean joined() {
        return this.joined;
    }

    void joined(boolean joined) {
        this.joined = joined;
    }

    CompletableFuture<Void> preparation() {
        return this.preparation;
    }

    void preparation(CompletableFuture<Void> preparation) {
        this.preparation = preparation;
    }

    boolean prepared() {
        return this.prepared;
    }

    void prepared(boolean prepared) {
        this.prepared = prepared;
    }

    CompletableFuture<Void> ending() {
        return this.ending;
    }

    void ending(CompletableFuture<Void> ending) {
        this.ending = ending;
    }

    /**
     * 向客户端发送原版 NMS 数据包.
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
     * 向客户端发送当前服务端协议的包 ID 和 payload, 不带长度、压缩或加密头.
     *
     * @param frame 已写入包 ID 和 payload 的可读缓冲
     */
    public void sendByteBuf(@NotNull ByteBuf frame) {
        this.transfer(frame, true, true);
    }

    /**
     * 模拟客户端发送原版 NMS 数据包.
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
     * 模拟收到当前服务端协议的包 ID 和 payload, 从原版解码器之前进入入站处理链.
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