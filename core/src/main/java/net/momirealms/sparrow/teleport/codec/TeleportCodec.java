package net.momirealms.sparrow.teleport.codec;

import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;

public interface TeleportCodec {

    /**
     * 返回组件的类型 ID, 接收方靠它找到解码函数.
     *
     * @return 类型 ID, 同一种组件始终返回同一个值
     */
    @NotNull
    SparrowKey type();

    /**
     * 把组件自己的参数写成 NBT. 接收方的解码函数要能从这份 NBT 还原出等价的组件.
     *
     * @return 组件参数
     */
    @NotNull
    CompoundTag toNbt();
}