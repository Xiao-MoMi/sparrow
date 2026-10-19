package net.momirealms.sparrow.teleport.processor.target.safe;

import net.minecraft.world.level.Level;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.ListTag;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessor;
import net.momirealms.sparrow.teleport.search.ChunkPolicy;
import net.momirealms.sparrow.teleport.search.SafetyRule;
import net.momirealms.sparrow.teleport.search.SearchContext;
import net.momirealms.sparrow.teleport.search.SearchPattern;
import net.momirealms.sparrow.teleport.search.SearchSession;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 在目标服按指定模式搜索位置, 采用通过全部所选规则的候选.
 * 模式和规则都按调用方提供的顺序执行, 各个模式共用检查次数和搜索时限.
 * 配置中的列表会复制为不可变列表, 同一个处理器可以用于多次传送.
 *
 * <p>输入原点是执行到此处理器时的位置, 可能已经被前面的处理器修改.
 * 搜索无结果时, {@link TeleportContext#UNSAFE} 为 true 则保留这个原点, 否则拒绝传送.
 * 无效输入和实际异常沿拒绝或异常路径结束, 不使用 unsafe 回退.
 *
 * <pre>{@code
 * SearchPattern nearby = SearchPattern.nearby()
 *         .horizontalRadius(8)
 *         .up(16)
 *         .down(16)
 *         .order(SearchPattern.SearchOrder.NEAR_FIRST)
 *         .build();
 * FindSafeLoc findSafe = FindSafeLoc.builder()
 *         .search(SearchPattern.exact(), nearby)
 *         .rules(PhysicalSafety.builder().build())
 *         .chunks(ChunkPolicy.EXISTING_ONLY)
 *         .maxChecks(10_000)
 *         .timeout(Duration.ofSeconds(3))
 *         .build();
 * // 将 findSafe 传给 Teleport.builder().targetProcessor(findSafe).
 * }</pre>
 *
 * @param search 按优先级排列的搜索模式, 至少包含一项
 * @param rules 每个候选必须依次通过的安全规则, 至少包含一项
 * @param chunks 本次搜索的区块加载策略
 * @param maxChecks 所有模式合计可检查的候选数, 必须大于 0
 * @param timeout 搜索时限, 必须为正且能以 long 纳秒表示
 */
public record FindSafeLoc(
        @NotNull List<SearchPattern> search,
        @NotNull List<SafetyRule> rules,
        @NotNull ChunkPolicy chunks,
        int maxChecks,
        @NotNull Duration timeout
) implements PositionProcessor {
    public static final SparrowKey TYPE = SparrowKey.sparrow("find-safe-loc");

    public FindSafeLoc {
        if (search.isEmpty()) throw new IllegalArgumentException("Safe location search requires at least one search pattern");
        if (rules.isEmpty()) throw new IllegalArgumentException("Safe location search requires at least one safety rule");
        if (maxChecks <= 0) throw new IllegalArgumentException("Safe location search requires a positive check budget");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("Safe location search requires a positive timeout");
        timeout.toNanos();
        search = List.copyOf(search);
        rules = List.copyOf(rules);
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    @NotNull
    public static FindSafeLoc fromNbt(@NotNull CompoundTag tag) {
        if (!(tag.get("search") instanceof ListTag search) || !(tag.get("rules") instanceof ListTag rules)) {
            throw new IllegalArgumentException("Safe location search requires search and rules lists");
        }
        return new FindSafeLoc(
                SearchPattern.TYPES.decodeList(search),
                SafetyRule.TYPES.decodeList(rules),
                ChunkPolicy.valueOf(tag.getString("chunks")),
                tag.getInt("max_checks", 0),
                Duration.ofNanos(tag.getLong("timeout_nanos", 0))
        );
    }

    @NotNull
    @Override
    public SparrowKey type() {
        return TYPE;
    }

    @NotNull
    @Override
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.put("search", SearchPattern.TYPES.encodeList(this.search));
        tag.put("rules", SafetyRule.TYPES.encodeList(this.rules));
        tag.putString("chunks", this.chunks.name());
        tag.putInt("max_checks", this.maxChecks);
        tag.putLong("timeout_nanos", this.timeout.toNanos());
        return tag;
    }

    // 从当前输入位置开始搜索, 将合格结果转换为处理器的接受或重定位结果.
    @NotNull
    @Override
    public CompletableFuture<Result> process(@NotNull TeleportContext context, @NotNull WorldLocation location) {
        Location resolved = location.resolve();
        if (resolved == null) return CompletableFuture.completedFuture(new Result.Reject(MessageConstants.TELEPORT_REJECTED_INVALID));
        if (location.x() < -Level.MAX_LEVEL_SIZE || location.x() >= Level.MAX_LEVEL_SIZE || location.z() < -Level.MAX_LEVEL_SIZE || location.z() >= Level.MAX_LEVEL_SIZE) {
            return CompletableFuture.completedFuture(new Result.Reject(MessageConstants.TELEPORT_REJECTED_INVALID));
        }
        boolean unsafe = context.toNbt().getBoolean(TeleportContext.UNSAFE);
        SearchContext environment = new SearchContext(context, resolved.getWorld(), this.chunks);
        CompletableFuture<WorldLocation> searching = new SearchSession(environment, location, this.search, this.rules, this.maxChecks, this.timeout).find();
        CompletableFuture<Result> result = searching.thenApply(found -> {
            if (found != null) return found.equals(location) ? Result.Accept.INSTANCE : new Result.Relocate(found);
            return unsafe ? Result.Accept.INSTANCE : new Result.Reject(MessageConstants.TELEPORT_REJECTED_UNSAFE);
        });
        // 处理器链可以取消当前结果, 搜索会在下一处推进边界停止, 已发起的加载自行结束.
        result.whenComplete((value, error) -> {
            if (error != null) searching.cancel(false);
        });
        return result;
    }

    public static final class Builder {
        private @Nullable List<SearchPattern> search; // 模式顺序, null 表示未填写
        private @Nullable List<SafetyRule> rules;     // 规则顺序, null 表示未填写
        private @Nullable ChunkPolicy chunks;        // 区块策略, null 表示未填写
        private int maxChecks;                       // 候选总预算, 0 表示未填写
        private @Nullable Duration timeout;          // 搜索时限, null 表示未填写

        private Builder() {
        }

        /**
         * 按优先级指定搜索模式.
         *
         * @param search 至少一个搜索模式, 顺序就是实际搜索顺序
         * @return 此构建器
         */
        @NotNull
        public Builder search(@NotNull SearchPattern... search) {
            this.search = List.of(search);
            return this;
        }

        /**
         * 指定每个候选必须依次通过的规则.
         *
         * @param rules 至少一条规则, false 会跳过当前候选的后续规则
         * @return 此构建器
         */
        @NotNull
        public Builder rules(@NotNull SafetyRule... rules) {
            this.rules = List.of(rules);
            return this;
        }

        /**
         * 指定本次搜索的区块加载与生成策略.
         *
         * @param chunks 区块策略
         * @return 此构建器
         */
        @NotNull
        public Builder chunks(@NotNull ChunkPolicy chunks) {
            this.chunks = chunks;
            return this;
        }

        /**
         * 指定所有模式共用的候选检查次数上限, 原点和无法取得区块的候选也计入次数.
         *
         * @param maxChecks 正的检查次数上限, 在 build 时校验
         * @return 此构建器
         */
        @NotNull
        public Builder maxChecks(int maxChecks) {
            this.maxChecks = maxChecks;
            return this;
        }

        /**
         * 指定包含区块等待和异步规则等待的搜索时限.
         * 到期后停止接受后续结果, 已经找到的合格位置仍可作为搜索结果返回.
         *
         * @param timeout 正的时限, 必须能以 long 纳秒表示
         * @return 此构建器
         */
        @NotNull
        public Builder timeout(@NotNull Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        @NotNull
        public FindSafeLoc build() {
            if (this.search == null) throw new IllegalStateException("Safe location search patterns are not specified");
            if (this.rules == null) throw new IllegalStateException("Safe location safety rules are not specified");
            if (this.chunks == null) throw new IllegalStateException("Safe location chunk policy is not specified");
            if (this.timeout == null) throw new IllegalStateException("Safe location timeout is not specified");
            return new FindSafeLoc(this.search, this.rules, this.chunks, this.maxChecks, this.timeout);
        }
    }
}