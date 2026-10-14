package net.momirealms.sparrow.teleport.processor.target;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class PositionProcessorChain {
    private final TeleportContext context;
    private final List<PositionProcessor> processors;
    private final Executor executor = VersionHelper.hasPaperPatch ? SparrowPlugin.instance().scheduler().async() : SparrowPlugin.instance().scheduler().platform();
    private final CompletableFuture<PositionProcessor.Result> completion = new CompletableFuture<>();
    private volatile @Nullable CompletableFuture<PositionProcessor.Result> pending;
    private WorldLocation location;
    private boolean relocated;
    private int index;

    public PositionProcessorChain(@NotNull TeleportContext context, @NotNull WorldLocation location, @NotNull List<PositionProcessor> processors) {
        this.context = context;
        this.location = location;
        this.processors = processors;
        this.completion.whenComplete((result, error) -> {
            CompletableFuture<PositionProcessor.Result> current = this.pending;
            if (error != null && current != null) current.cancel(false);
        });
    }

    @NotNull
    public CompletableFuture<PositionProcessor.Result> process() {
        this.advance();
        return this.completion;
    }

    private void advance() {
        try {
            while (!this.completion.isDone() && this.index < this.processors.size()) {
                PositionProcessor processor = this.processors.get(this.index++);
                this.pending = processor.process(this.context, this.location);
                if (this.completion.isDone()) {
                    this.pending.cancel(false);
                    return;
                }
                if (!this.pending.isDone()) {
                    this.pending.whenCompleteAsync((result, error) -> {
                        if (this.completion.isDone()) return;
                        if (error != null) {
                            this.completion.completeExceptionally(error);
                        } else if (this.apply(result)) {
                            this.advance();
                        }
                    }, this.executor);
                    return;
                }
                if (!this.apply(this.pending.join())) return;
            }
            if (!this.completion.isDone()) {
                this.completion.complete(this.relocated ? new PositionProcessor.Result.Relocate(this.location) : PositionProcessor.Result.Accept.INSTANCE);
            }
        } catch (Throwable error) {
            this.completion.completeExceptionally(error);
        }
    }

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