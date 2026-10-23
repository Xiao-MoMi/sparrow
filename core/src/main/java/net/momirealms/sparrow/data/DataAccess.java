package net.momirealms.sparrow.data;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.data.access.MultiWriterData;
import net.momirealms.sparrow.data.access.SingleWriterData;
import net.momirealms.sparrow.data.definition.MultiWriterDefinition;
import net.momirealms.sparrow.data.definition.SingleWriterDefinition;
import net.momirealms.sparrow.data.internal.MultiWriterDataImpl;
import net.momirealms.sparrow.data.internal.RegisteredData;
import net.momirealms.sparrow.data.internal.SingleWriterDataImpl;
import net.momirealms.sparrow.data.message.DataChangedMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/** 常驻数据入口. 业务保存 register 返回的对象, 后续直接通过它访问数据. */
public final class DataAccess {
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Map<SparrowKey, RegisteredData<?, ?>> registrations = new HashMap<>();
    private final ScheduledThreadPoolExecutor expiryExecutor = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().name("sparrow-data-expiry").daemon(true).factory());
    private byte[] messageHeader;
    private boolean enabled;
    private CompletableFuture<Void> closing;

    @ApiStatus.Internal
    public void onLoad() {
        this.expiryExecutor.setRemoveOnCancelPolicy(true);
        MessageBroker<FriendlyByteBuf> broker = this.plugin.messageBrokerManager().broker();
        ByteBuf buffer = Unpooled.buffer();
        try {
            ByteBufHelper.writeCompactInt(buffer, broker.registry().byId(DataChangedMessage.ID).id());
            this.messageHeader = ByteBufUtil.getBytes(buffer);
        } finally {
            buffer.release();
        }
    }

    @ApiStatus.Internal
    public synchronized void onEnable() {
        this.enabled = true;
    }

    /** 注册单写数据并返回它的入口. 在启用阶段调用, 同一 ID 不能重复注册. */
    @NotNull
    public synchronized <E, V> SingleWriterData<E, V> register(@NotNull SingleWriterDefinition<E, V> definition) {
        if (!this.enabled) throw new IllegalStateException("Data access is not enabled");
        if (this.registrations.containsKey(definition.id())) throw new IllegalArgumentException("Data already registered: " + definition.id());
        SingleWriterDataImpl<E, V> data = new SingleWriterDataImpl<>(definition);
        this.registrations.put(definition.id(), data);
        data.schedule(this.expiryExecutor);
        return data;
    }

    /** 注册多写数据并返回它的入口, 约束与单写注册相同. */
    @NotNull
    public synchronized <E, V> MultiWriterData<E, V> register(@NotNull MultiWriterDefinition<E, V> definition) {
        if (!this.enabled) throw new IllegalStateException("Data access is not enabled");
        if (this.registrations.containsKey(definition.id())) throw new IllegalArgumentException("Data already registered: " + definition.id());
        MultiWriterDataImpl<E, V> data = new MultiWriterDataImpl<>(definition);
        this.registrations.put(definition.id(), data);
        data.schedule(this.expiryExecutor);
        return data;
    }

    @ApiStatus.Internal
    public void receive(@NotNull DataChangedMessage message) {
        RegisteredData<?, ?> data;
        synchronized (this) {
            if (!this.enabled) return;
            data = this.registrations.get(message.id());
        }
        if (data != null) {
            data.receive(message);
        }
    }

    /** 关闭并移除一个类型, 之后可以重新注册. 不删除 Redis 或数据库中的值. */
    @NotNull
    public synchronized CompletableFuture<Void> unregister(@NotNull SparrowKey id) {
        if (!this.enabled) return CompletableFuture.failedFuture(new IllegalStateException("Data access is not enabled"));
        RegisteredData<?, ?> data = this.registrations.get(id);
        if (data == null) return CompletableFuture.completedFuture(null);
        return data.closeAsync().thenRun(() -> {
            synchronized (this) {
                this.registrations.remove(id, data);
            }
        }).copy();
    }

    /** 等待所有已接受的操作后关闭. 不会自动保存, 写容器中未发布的修改随之丢弃. */
    @NotNull
    public synchronized CompletableFuture<Void> closeAsync() {
        if (this.closing != null) return this.closing.copy();
        this.enabled = false;
        List<CompletableFuture<Void>> closing = new ArrayList<>(this.registrations.size());
        for (RegisteredData<?, ?> data : this.registrations.values()) {
            closing.add(data.closeAsync());
        }
        this.closing = CompletableFuture.allOf(closing.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> {
            synchronized (this) {
                this.registrations.clear();
            }
            this.expiryExecutor.shutdown();
        });
        return this.closing.copy();
    }

    @ApiStatus.Internal
    public byte @NotNull [] messageHeader() {
        return this.messageHeader;
    }
}