package net.momirealms.sparrow.teleport.codec;

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.ListTag;
import net.momirealms.sparrow.util.SparrowKey;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class TeleportCodecRegistry<T extends TeleportCodec> {
    private final ConcurrentChainedObject2ObjectHashTable<SparrowKey, Function<CompoundTag, ? extends T>> decoders = new ConcurrentChainedObject2ObjectHashTable<>(); // 类型 ID -> 解码函数

    /**
     * 注册一种组件.
     *
     * @param id 类型 ID, 必须和组件 {@link TeleportCodec#type()} 返回的一致
     * @param decoder 把 data 还原成组件的函数
     * @throws IllegalArgumentException 这个 ID 已经注册过时
     */
    public void register(@NotNull SparrowKey id, @NotNull Function<CompoundTag, ? extends T> decoder) {
        if (this.decoders.putIfAbsent(id, decoder) != null) {
            throw new IllegalArgumentException("Teleport component ID is already registered: " + id);
        }
    }

    /**
     * 把组件编码成带类型的 NBT.
     *
     * @param value 要编码的组件
     * @return 包含 type 和 data 的 NBT
     * @throws IllegalArgumentException 组件的类型没有注册时
     */
    @NotNull
    public CompoundTag encode(@NotNull T value) {
        SparrowKey id = value.type();
        if (this.decoders.get(id) == null) throw new IllegalArgumentException("Unregistered teleport component ID: " + id);
        CompoundTag tag = new CompoundTag();
        tag.putString("type", id.toString());
        tag.put("data", value.toNbt());
        return tag;
    }

    /**
     * 按 type 找到解码函数, 把 data 还原成组件.
     *
     * @param tag {@link #encode} 写出的 NBT
     * @return 还原出的组件
     * @throws IllegalArgumentException 类型没有注册, 或者缺少 data 时
     */
    @NotNull
    public T decode(@NotNull CompoundTag tag) {
        SparrowKey id = SparrowKey.of(tag.getString("type"));
        Function<CompoundTag, ? extends T> decoder = this.decoders.get(id);
        if (decoder == null) throw new IllegalArgumentException("Unknown teleport component ID: " + id);
        if (!(tag.get("data") instanceof CompoundTag data)) throw new IllegalArgumentException("Missing compound data for teleport component: " + id);
        return decoder.apply(data);
    }

    /**
     * 按顺序编码一组组件.
     *
     * @param values 组件列表
     * @return 每一项都是 {@link #encode} 结果的列表, 顺序不变
     * @throws IllegalArgumentException 某个组件的类型没有注册时
     */
    @NotNull
    public ListTag encodeList(@NotNull List<? extends T> values) {
        ListTag tags = new ListTag();
        for (int index = 0, size = values.size(); index < size; index++) {
            tags.add(this.encode(values.get(index)));
        }
        return tags;
    }

    /**
     * 按顺序解码一组组件.
     * 处理器的执行顺序就是列表顺序, 所以这里必须保持编码时的顺序.
     *
     * @param tags {@link #encodeList} 写出的列表
     * @return 组件列表
     * @throws IllegalArgumentException 列表里有不是 CompoundTag 的元素, 或者某一项无法解码时
     */
    @NotNull
    public List<T> decodeList(@NotNull ListTag tags) {
        int size = tags.size();
        List<T> values = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            if (!(tags.get(index) instanceof CompoundTag tag)) throw new IllegalArgumentException("Teleport component list must contain compounds");
            values.add(this.decode(tag));
        }
        return values;
    }
}