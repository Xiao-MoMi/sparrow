package net.momirealms.sparrow.teleport.processor.target;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class PositionProcessorChain {
    private final TeleportContext context;                                                            // 传给每个处理器的上下文
    private final List<PositionProcessor> processors;                                                 // 按顺序执行的处理器
    private final Executor executor = SparrowPlugin.instance().scheduler().async();                   // 异步完成的处理器在这里接着执行后面的步骤
    private final CompletableFuture<PositionProcessor.Result> completion = new CompletableFuture<>(); // 整条链的结果
    private WorldLocation location; // 当前坐标, 处理器 Relocate 后更新
    private boolean relocated;      // 有没有处理器换过坐标
    private int index;              // 下一个要执行的处理器下标

    public PositionProcessorChain(@NotNull TeleportContext context, @NotNull WorldLocation location, @NotNull List<PositionProcessor> processors) {
        this.context = context;
        this.location = location;
        this.processors = processors;
    }

    /**
     * 开始执行整条链.
     */
    @NotNull
    public CompletableFuture<PositionProcessor.Result> process() {
        this.advance();
        return this.completion;
    }

    /**
     * 从 index 开始往后执行处理器, 直到遇到一个异步完成的处理器, 或者整条链结束.
     */
    private void advance() {
        try {
            while (this.index < this.processors.size()) {
                PositionProcessor processor = this.processors.get(this.index++);
                CompletableFuture<PositionProcessor.Result> pending = processor.process(this.context, this.location);
                // 异步完成的处理器在回调里接着往下跑, 当前线程到这里就结束
                if (!pending.isDone()) {
                    pending.whenCompleteAsync((result, error) -> {
                        if (error != null) {
                            this.completion.completeExceptionally(error);
                        } else if (this.apply(result)) {
                            this.advance();
                        }
                    }, this.executor);
                    return;
                }
                // 同步完成的处理器直接取结果, 被拒绝时结束
                if (!this.apply(pending.join())) return;
            }
            // 全部处理器通过后, 按坐标是否变更返回结果
            this.completion.complete(this.relocated ? new PositionProcessor.Result.Relocate(this.location) : PositionProcessor.Result.Accept.INSTANCE);
        } catch (Throwable error) {
            this.completion.completeExceptionally(error);
        }
    }

    /**
     * 把一个处理器的结果应用到链上. Reject 直接结束整条链, Relocate 更新当前坐标.
     *
     * @param result 处理器的结果
     * @return 要继续执行下一个处理器时返回 true, 被拒绝时返回 false
     */
    private boolean apply(PositionProcessor.Result result) {
        return switch (result) {
            case PositionProcessor.Result.Accept ignored -> true;
            case PositionProcessor.Result.Reject rejected -> {
                this.completion.complete(rejected);
                yield false;
            }
            case PositionProcessor.Result.Relocate changed -> {
                this.location = changed.location();
                this.relocated = true;
                yield true;
            }
        };
    }
}