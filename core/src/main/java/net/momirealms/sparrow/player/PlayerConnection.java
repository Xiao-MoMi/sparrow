package net.momirealms.sparrow.player;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.ReferenceCountUtil;
import net.momirealms.sparrow.data.access.SingleWriterData;
import net.momirealms.sparrow.data.access.SingleWriterEntries;
import net.momirealms.sparrow.proxy.minecraft.network.ConnectionProxy;
import net.momirealms.sparrow.proxy.minecraft.network.ProtocolSwapHandlerProxy;
import net.momirealms.sparrow.proxy.minecraft.network.protocol.PacketProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class PlayerConnection {
    // 会话结束需要通道已关闭, 不会再写入, 准备已落定三者同时成立
    private static final int CLOSED = 1;
    private static final int WRITES_DONE = 2; // 关闭时没进入游戏, 或退出监听器已全部结束
    private static final int PREPARING = 4;   // 准备已开始, 取锁, 加载与准备监听器还没结束
    private static final int ENDED = 8;       // 收尾已交给 PlayerManager

    private final UUID uniqueId;
    private final String name;
    private final InetAddress ip;
    private final ChannelHandler handle;
    private final Channel channel;
    private final long connectedAt;
    private boolean joined;
    private volatile CompletableFuture<Void> preparation; // 取锁, 加载档案与准备监听器的整体结果, 尚未开始准备时为 null
    private volatile byte[] lockToken; // 本次取得的玩家锁值, 释放锁时交回, 尚未取得时为 null
    private volatile boolean prepared;
    private final AtomicInteger state = new AtomicInteger();
    private final CompletableFuture<Void> ended = new CompletableFuture<>(); // 会话收尾结束时完成
    private final Map<SingleWriterData<?, ?>, CompletableFuture<? extends SingleWriterEntries<?, ?>>> writers = new HashMap<>(); // 本次会话加载的写容器, 在本对象锁下读写
    private boolean writersClosed;

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

    byte[] lockToken() {
        return this.lockToken;
    }

    void lockToken(byte[] lockToken) {
        this.lockToken = lockToken;
    }

    boolean prepared() {
        return this.prepared;
    }

    void prepared(boolean prepared) {
        this.prepared = prepared;
    }

    // 取得本次会话中这个玩家的写容器, 同一份数据只加载一次. 会话结束时统一保存并关闭, 调用方不要自行 close.
    // 只能在取得玩家锁之后调用, 也就是准备监听器中或进入游戏之后; 会话收尾开始后调用会失败
    @NotNull
    public <E, V> CompletableFuture<SingleWriterEntries<E, V>> load(@NotNull SingleWriterData<E, V> data) {
        CompletableFuture<? extends SingleWriterEntries<?, ?>> loading;
        synchronized (this) {
            if (this.writersClosed) return CompletableFuture.failedFuture(new IllegalStateException("Player session has ended"));
            loading = this.writers.computeIfAbsent(data, ignored -> data.load(this.uniqueId));
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<SingleWriterEntries<E, V>> entries = (CompletableFuture<SingleWriterEntries<E, V>>) loading;
        return entries.copy();
    }

    // 会话收尾时调用, 之后不能再加载. 加载失败已交给发起加载的一方, 这里只保存并关闭加载成功的写容器
    @NotNull
    CompletableFuture<Void> closeWriters() {
        List<CompletableFuture<? extends SingleWriterEntries<?, ?>>> loaded;
        synchronized (this) {
            this.writersClosed = true;
            loaded = new ArrayList<>(this.writers.values());
        }
        CompletableFuture<?>[] closing = new CompletableFuture<?>[loaded.size()];
        for (int i = 0; i < closing.length; i++) {
            closing[i] = loaded.get(i).handle((writer, error) -> writer).thenCompose(writer -> {
                if (writer == null) return CompletableFuture.completedFuture(null);
                // close 会等先接受的 save 结束, 保存失败由合并后的 Future 带出
                return CompletableFuture.allOf(writer.save(), writer.close());
            });
        }
        return CompletableFuture.allOf(closing);
    }

    // 通道已关闭时拒绝开始准备
    boolean beginPreparation() {
        while (true) {
            int current = this.state.get();
            if ((current & CLOSED) != 0) return false;
            if (this.state.compareAndSet(current, current | PREPARING)) return true;
        }
    }

    boolean finishPreparation() {
        return this.update(0, PREPARING);
    }

    boolean markClosed(boolean writesDone) {
        return this.update(writesDone ? CLOSED | WRITES_DONE : CLOSED, 0);
    }

    boolean markWritesDone() {
        return this.update(WRITES_DONE, 0);
    }

    boolean isClosed() {
        return (this.state.get() & CLOSED) != 0;
    }

    // 三个条件刚好凑齐的那次调用返回 true, 由调用方执行唯一一次收尾
    private boolean update(int add, int remove) {
        while (true) {
            int current = this.state.get();
            int next = (current | add) & ~remove;
            boolean complete = (next & (CLOSED | WRITES_DONE | PREPARING | ENDED)) == (CLOSED | WRITES_DONE);
            if (complete) {
                next |= ENDED;
            }
            if (this.state.compareAndSet(current, next)) return complete;
        }
    }

    CompletableFuture<Void> ended() {
        return this.ended;
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