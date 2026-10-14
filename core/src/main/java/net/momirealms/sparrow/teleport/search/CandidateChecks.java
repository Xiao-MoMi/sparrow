package net.momirealms.sparrow.teleport.search;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.processor.TeleportProcessor;
import net.momirealms.sparrow.teleport.processor.target.SafeLocationRule;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class CandidateChecks {
    private final CandidateContext context;
    private final List<TeleportProcessor.CandidateRule> rules;
    private final CompletableFuture<?> completion;
    private final Executor executor = VersionHelper.hasPaperPatch ? SparrowPlugin.instance().scheduler().async() : SparrowPlugin.instance().scheduler().platform();
    private @Nullable TeleportProcessor.CandidateRule rejectedRule; // 本轮正常完成后可读取拒绝来源

    public CandidateChecks(@NotNull CandidateContext context, @NotNull List<TeleportProcessor.CandidateRule> rules, @NotNull CompletableFuture<?> completion) {
        this.context = context;
        this.rules = rules;
        this.completion = completion;
    }

    @NotNull
    public CompletableFuture<Component> check(@NotNull WorldLocation location) {
        this.rejectedRule = null;
        return this.check(location, 0, false);
    }

    // 强制回退仅豁免内置安全规则, 其余规则继续判定回退后的坐标
    @NotNull
    public CompletableFuture<Component> checkFallback(@NotNull WorldLocation location) {
        this.rejectedRule = null;
        return this.check(location, 0, true);
    }

    @Nullable
    public TeleportProcessor.CandidateRule rejectedRule() {
        return this.rejectedRule;
    }

    private CompletableFuture<Component> check(WorldLocation location, int start, boolean fallback) {
        for (int index = start, size = this.rules.size(); index < size; index++) {
            TeleportProcessor.CandidateRule rule = this.rules.get(index);
            if (fallback && rule instanceof SafeLocationRule) continue;
            CompletableFuture<Component> checked = rule.check(this.context, location);
            if (!checked.isDone()) {
                this.completion.whenComplete((found, error) -> {
                    if (error != null) checked.cancel(false);
                });
                int next = index + 1;
                return checked.thenComposeAsync(reason -> {
                    if (reason != null) {
                        this.rejectedRule = rule;
                        return CompletableFuture.completedFuture(reason);
                    }
                    return this.check(location, next, fallback);
                }, this.executor);
            }
            if (checked.getNow(null) != null) {
                this.rejectedRule = rule;
                return checked;
            }
        }
        return TeleportProcessor.PASS;
    }
}