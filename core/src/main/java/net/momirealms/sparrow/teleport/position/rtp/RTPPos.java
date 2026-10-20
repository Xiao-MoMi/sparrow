package net.momirealms.sparrow.teleport.position.rtp;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.ListTag;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.search.ChunkPolicy;
import net.momirealms.sparrow.teleport.search.SafetyRule;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 在目标服的指定区域内随机寻找落脚点, 逐条检查所选规则, 找到后作为传送目的地返回.
 * 区域和规则列表会复制为不可变列表, 同一份配置可以用于多次请求.
 * 每个世界对应一个 RtpArea, 同世界的多个矩形放入该区域的矩形列表, 高度范围由各矩形独立指定.
 *
 * <pre>{@code
 * RtpArea area = RtpArea.builder("world")
 *         .rectangle(0, 0, 999, 999)
 *         .rectangle(1500, -500, 2499, 499, 60, 100)
 *         .build();
 * RTPPos settings = RTPPos.builder()
 *         .areas(area)
 *         .nearbyColumns(2, 4, 4)
 *         .rules(physical, land)
 *         .chunks(ChunkPolicy.ALLOW_GENERATION)
 *         .maxAttempts(32)
 *         .maxChecks(10_000)
 *         .timeout(Duration.ofSeconds(9))
 *         .build();
 * // 将 settings 传给 Teleport.builder().position(settings).
 * }</pre>
 *
 * @param areas 非空的区域列表, 每个世界只出现一次, 所有世界属于同一个目标服务器
 * @param scanMode 列扫描方式, AUTO 根据当前目标世界选择地表或从上往下寻找空腔
 * @param ignoreLeaves 扫描时是否跳过树叶并继续寻找下方地面
 * @param stopAtFluid 扫描路径遇到流体时是否放弃当前列
 * @param nearbyRadius 附近列的水平半径, 单位为方块, 0 表示不搜索附近列
 * @param nearbyUp 附近搜索相对初始候选向上的脚底距离, 单位为方块
 * @param nearbyDown 附近搜索相对初始候选向下的脚底距离, 单位为方块
 * @param rules 非空的有序安全规则列表
 * @param chunks 所有世界共用的区块加载策略
 * @param maxAttempts 随机起点的总尝试次数上限, 必须大于 0
 * @param maxChecks 整轮搜索的检查次数上限, 必须大于 0
 * @param timeout 整轮搜索时限, 必须为正且能以 long 纳秒表示
 */
public record RTPPos(
        @NotNull List<RtpArea> areas,
        @NotNull ScanMode scanMode,
        boolean ignoreLeaves,
        boolean stopAtFluid,
        int nearbyRadius,
        int nearbyUp,
        int nearbyDown,
        @NotNull List<SafetyRule> rules,
        @NotNull ChunkPolicy chunks,
        int maxAttempts,
        int maxChecks,
        @NotNull Duration timeout
) implements TeleportPosition {
    public static final SparrowKey TYPE = SparrowKey.sparrow("rtp"); // 随机目的地配置的类型标识

    public RTPPos {
        if (areas.isEmpty()) throw new IllegalArgumentException("RTP requires at least one area");
        if (rules.isEmpty()) throw new IllegalArgumentException("RTP requires at least one safety rule");
        if (nearbyRadius < 0 || nearbyUp < 0 || nearbyDown < 0) throw new IllegalArgumentException("RTP requires non-negative nearby distances");
        if (maxAttempts <= 0 || maxChecks <= 0) throw new IllegalArgumentException("RTP requires positive attempt and check budgets");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("RTP requires a positive timeout");
        timeout.toNanos();
        areas = List.copyOf(areas);
        rules = List.copyOf(rules);
        // 同一世界的所有矩形集中保存在一个区域中, 各矩形保留自己的高度范围.
        Set<String> worlds = new HashSet<>();
        for (int index = 0, size = areas.size(); index < size; index++) {
            String world = areas.get(index).world();
            if (!worlds.add(world)) throw new IllegalArgumentException("Duplicate RTP area world: " + world);
        }
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    @NotNull
    public static RTPPos fromNbt(@NotNull CompoundTag tag) {
        if (!(tag.get("areas") instanceof ListTag areaTags) || !(tag.get("rules") instanceof ListTag ruleTags)) {
            throw new IllegalArgumentException("RTP requires areas and rules lists");
        }
        List<RtpArea> areas = new ArrayList<>(areaTags.size());
        for (int index = 0, size = areaTags.size(); index < size; index++) {
            if (!(areaTags.get(index) instanceof CompoundTag area)) throw new IllegalArgumentException("RTP area list must contain compounds");
            areas.add(RtpArea.fromNbt(area));
        }
        return new RTPPos(
                areas,
                ScanMode.valueOf(tag.getString("scan_mode", "AUTO")),
                tag.getBoolean("ignore_leaves", true),
                tag.getBoolean("stop_at_fluid", true),
                tag.getInt("nearby_radius", -1),
                tag.getInt("nearby_up", -1),
                tag.getInt("nearby_down", -1),
                SafetyRule.TYPES.decodeList(ruleTags),
                ChunkPolicy.valueOf(tag.getString("chunks", "")),
                tag.getInt("max_attempts", 0),
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
        ListTag areas = new ListTag();
        for (int index = 0, size = this.areas.size(); index < size; index++) {
            areas.add(this.areas.get(index).toNbt());
        }
        tag.put("areas", areas);
        tag.putString("scan_mode", this.scanMode.name());
        tag.putBoolean("ignore_leaves", this.ignoreLeaves);
        tag.putBoolean("stop_at_fluid", this.stopAtFluid);
        tag.putInt("nearby_radius", this.nearbyRadius);
        tag.putInt("nearby_up", this.nearbyUp);
        tag.putInt("nearby_down", this.nearbyDown);
        tag.put("rules", SafetyRule.TYPES.encodeList(this.rules));
        tag.putString("chunks", this.chunks.name());
        tag.putInt("max_attempts", this.maxAttempts);
        tag.putInt("max_checks", this.maxChecks);
        tag.putLong("timeout_nanos", this.timeout.toNanos());
        return tag;
    }

    @NotNull
    @Override
    public CompletableFuture<Result> resolve(@NotNull TeleportContext context) {
        return new RtpSearch(context, this).find();
    }

    public static final class Builder {
        private @Nullable List<RtpArea> areas;     // 每个世界一项区域配置, null 表示未填写
        private ScanMode scanMode = ScanMode.AUTO; // 默认按当前世界是否有顶选择扫描方式
        private boolean ignoreLeaves = true;      // 默认穿过树叶寻找地面
        private boolean stopAtFluid = true;       // 默认在扫描路径遇到流体时放弃当前列
        private @Nullable Integer nearbyRadius;  // 附近列水平半径, null 表示整组附近范围未填写
        private int nearbyUp;                     // 附近搜索向上距离, 由 nearbyColumns 一起设置
        private int nearbyDown;                   // 附近搜索向下距离, 由 nearbyColumns 一起设置
        private @Nullable List<SafetyRule> rules; // 所选规则的执行顺序, null 表示未填写
        private @Nullable ChunkPolicy chunks;    // 区块加载策略, null 表示未填写
        private int maxAttempts;                  // 随机起点总预算, 0 表示未填写
        private int maxChecks;                    // 整轮检查总预算, 0 表示未填写
        private @Nullable Duration timeout;       // 整轮搜索时限, null 表示未填写

        private Builder() {
        }

        /**
         * 指定同一目标服务器上的多世界区域, 替换此前设置的区域列表.
         * 同世界的多个矩形需要放在同一个 RtpArea 中.
         *
         * @param areas 至少一个区域, 世界名必须唯一
         * @return 此构建器
         */
        @NotNull
        public Builder areas(@NotNull RtpArea... areas) {
            this.areas = List.of(areas);
            return this;
        }

        /**
         * 指定从哪个方向寻找落脚面, 覆盖默认的 AUTO.
         * AUTO 在普通世界找露天地表, 在下界这类有顶世界从逻辑顶层以下向下找空腔.
         *
         * @param scanMode 扫描方式, 对本次 RTP 的各个世界生效
         * @return 此构建器
         */
        @NotNull
        public Builder scanMode(@NotNull ScanMode scanMode) {
            this.scanMode = scanMode;
            return this;
        }

        /**
         * 指定列扫描是否穿过树叶, 继续寻找下方地面.
         * 最终身体是否碰撞树叶, 以及树叶能否提供支撑, 由所选安全规则判断.
         *
         * @param ignoreLeaves true 表示扫描时跳过树叶, 默认 true
         * @return 此构建器
         */
        @NotNull
        public Builder ignoreLeaves(boolean ignoreLeaves) {
            this.ignoreLeaves = ignoreLeaves;
            return this;
        }

        /**
         * 指定列扫描路径遇到流体时是否放弃当前列.
         * 此选项控制扫描能否继续向下, 最终落点的流体接触由安全规则判断.
         *
         * @param stopAtFluid true 表示遇到流体便放弃当前列, 默认 true
         * @return 此构建器
         */
        @NotNull
        public Builder stopAtFluid(boolean stopAtFluid) {
            this.stopAtFluid = stopAtFluid;
            return this;
        }

        /**
         * 指定首次候选被拒绝后允许搜索的附近列范围.
         * 水平和垂直距离都以首次候选为基准, 所有距离以方块为单位.
         *
         * @param horizontalRadius 非负水平半径, 0 表示关闭附近列搜索
         * @param up 非负向上距离
         * @param down 非负向下距离
         * @return 此构建器
         */
        @NotNull
        public Builder nearbyColumns(int horizontalRadius, int up, int down) {
            this.nearbyRadius = horizontalRadius;
            this.nearbyUp = up;
            this.nearbyDown = down;
            return this;
        }

        /**
         * 指定本次 RTP 显式启用的规则, 替换此前设置的规则列表.
         * 前置阶段和完整坐标阶段使用同一列表, 前置 PASS 的规则由执行器跳过后置检查.
         *
         * @param rules 至少一条规则, 顺序保留在配置中
         * @return 此构建器
         */
        @NotNull
        public Builder rules(@NotNull SafetyRule... rules) {
            this.rules = List.of(rules);
            return this;
        }

        /**
         * 指定所有世界共用的区块加载与生成策略.
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
         * 指定随机起点的总尝试次数上限, 前置规则拒绝的随机起点同样计入.
         * 附近列属于当前随机起点的后续搜索, 不增加此计数.
         *
         * @param maxAttempts 正的随机尝试次数上限, 在 build 时校验
         * @return 此构建器
         */
        @NotNull
        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * 指定整轮 RTP 共用的检查预算.
         * 每进入一列扣一次, 每扫描一个 Y 层再扣一次, 该层的规则链不按规则条数重复扣除.
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
         * 指定包含区域准备, 区块等待和规则等待的整轮搜索时限.
         * 切换世界, 随机重抽和附近搜索共用同一个截止时间.
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
        public RTPPos build() {
            if (this.areas == null) throw new IllegalStateException("RTP areas are not specified");
            if (this.nearbyRadius == null) throw new IllegalStateException("RTP nearby distances are not specified");
            if (this.rules == null) throw new IllegalStateException("RTP safety rules are not specified");
            if (this.chunks == null) throw new IllegalStateException("RTP chunk policy is not specified");
            if (this.timeout == null) throw new IllegalStateException("RTP timeout is not specified");
            return new RTPPos(
                    this.areas,
                    this.scanMode,
                    this.ignoreLeaves,
                    this.stopAtFluid,
                    this.nearbyRadius,
                    this.nearbyUp,
                    this.nearbyDown,
                    this.rules,
                    this.chunks,
                    this.maxAttempts,
                    this.maxChecks,
                    this.timeout
            );
        }
    }

    /**
     * 决定一列从哪里开始寻找落脚面.
     */
    public enum ScanMode {
        AUTO,             // 有顶世界选 CAVITY_TOP_DOWN, 其他世界选 SURFACE
        SURFACE,          // 从地表上方往下找最上面的落脚面, 使用实际世界高度
        CAVITY_TOP_DOWN,  // 从允许范围上端向下穿过岩层, 找到空腔后寻找其地板; 有顶世界还受逻辑顶层限制
        CAVITY_BOTTOM_UP  // 从允许范围下端向上穿过岩层, 寻找第一段有地板的空腔
    }
}