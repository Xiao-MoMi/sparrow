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
    private final ConcurrentChainedObject2ObjectHashTable<SparrowKey, Function<CompoundTag, ? extends T>> decoders = new ConcurrentChainedObject2ObjectHashTable<>();

    public void register(@NotNull SparrowKey id, @NotNull Function<CompoundTag, ? extends T> decoder) {
        if (this.decoders.putIfAbsent(id, decoder) != null) {
            throw new IllegalArgumentException("Teleport component ID is already registered: " + id);
        }
    }

    @NotNull
    public CompoundTag encode(@NotNull T value) {
        SparrowKey id = value.type();
        if (this.decoders.get(id) == null) throw new IllegalArgumentException("Unregistered teleport component ID: " + id);
        CompoundTag tag = new CompoundTag();
        tag.putString("type", id.toString());
        tag.put("data", value.toNbt());
        return tag;
    }

    @NotNull
    public T decode(@NotNull CompoundTag tag) {
        SparrowKey id = SparrowKey.of(tag.getString("type"));
        Function<CompoundTag, ? extends T> decoder = this.decoders.get(id);
        if (decoder == null) throw new IllegalArgumentException("Unknown teleport component ID: " + id);
        if (!(tag.get("data") instanceof CompoundTag data)) throw new IllegalArgumentException("Missing compound data for teleport component: " + id);
        return decoder.apply(data);
    }

    @NotNull
    public ListTag encodeList(@NotNull List<? extends T> values) {
        ListTag tags = new ListTag();
        for (int index = 0, size = values.size(); index < size; index++) {
            tags.add(this.encode(values.get(index)));
        }
        return tags;
    }

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