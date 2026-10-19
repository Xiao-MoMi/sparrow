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
     * 检查当前候选是否满足此规则, 调用方在通过后继续检查列表中的下一条规则.
     * <p><strong>实现必须传播判断过程中的异常, 不得将异常转换成 false.</strong>
     * 同步抛出异常和 Future 异常完成都表示检查失败.
     * false 只拒绝当前候选, 后续选点及 unsafe 的无结果回退由搜索调用方负责.
     *
     * @param context 本次搜索共用的运行环境, 只读, 区块可按需准备
     * @param candidate 待判断的候选坐标, <strong>必须属于 context.world() 指定的世界</strong>
     * @return 判断结果的 Future, 正常完成值必须非空; true 表示通过此规则, false 表示拒绝当前候选, 异常完成表示检查失败
     */
    @NotNull
    CompletableFuture<Boolean> test(@NotNull SearchContext context, @NotNull WorldLocation candidate);
}