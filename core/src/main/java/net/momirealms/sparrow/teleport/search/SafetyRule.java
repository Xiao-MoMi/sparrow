package net.momirealms.sparrow.teleport.search;

import net.momirealms.sparrow.teleport.codec.TeleportCodec;
import net.momirealms.sparrow.teleport.codec.TeleportCodecRegistry;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public interface SafetyRule extends TeleportCodec {
    TeleportCodecRegistry<SafetyRule> TYPES = new TeleportCodecRegistry<>() {{
        this.register(PhysicalSafety.TYPE, PhysicalSafety::fromNbt);
    }};

    /**
     * 在解析落脚高度之前检查目标世界中的一列, 判断此规则能否提前完成.
     *
     * @param context 本次搜索共用的运行环境, 提供目标世界和业务数据, <strong>只读</strong>
     * @param blockX 待解析的方块列 X 坐标
     * @param blockZ 待解析的方块列 Z 坐标
     * @return 前置判断的 Future, 正常完成值必须非空; PASS 表示本规则已通过, REJECT 表示拒绝当前列, DEFER 表示待完整坐标再检查
     */
    @NotNull
    default CompletableFuture<BeforeResult> testBeforeResolve(@NotNull SearchContext context, int blockX, int blockZ) {
        return CompletableFuture.completedFuture(BeforeResult.DEFER);
    }

    /**
     * 检查当前候选是否满足此规则, 调用方在通过后继续检查列表中的下一条规则.
     * <p><strong>实现必须传播判断过程中的异常, 不得将异常转换成 false.</strong>
     *
     * @param context 本次搜索共用的运行环境, 只读, 区块可按需准备
     * @param candidate 待判断的候选坐标, <strong>必须属于 context.world() 指定的世界</strong>
     * @return 判断结果的 Future, 正常完成值必须非空; true 表示通过此规则, false 表示拒绝当前候选, 异常完成表示检查失败
     */
    @NotNull
    CompletableFuture<Boolean> test(@NotNull SearchContext context, @NotNull WorldLocation candidate);

    enum BeforeResult {
        PASS,   // 此规则已完成且通过
        REJECT, // 当前列不合格
        DEFER   // 此规则需要完整坐标
    }
}