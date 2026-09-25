package net.momirealms.sparrow.util;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemUtils {
    private ItemUtils() {}

    @NotNull
    @SuppressWarnings("unchecked")
    public static DataResult<Map<String, Object>> readableData(@NotNull ItemStack item, @NotNull HolderLookup.Provider registries, boolean full) {
        DynamicOps<Object> ops = registries.createSerializationContext(JavaOps.INSTANCE);
        return ItemStack.CODEC.encodeStart(ops, item).flatMap(encoded -> {
            Map<String, Object> data = new LinkedHashMap<>((Map<String, Object>) encoded);
            if (!full) return DataResult.success(data);
            return DataComponentMap.CODEC.encodeStart(ops, item.getComponents()).map(components -> {
                data.put("components", components);
                return data;
            });
        });
    }
}
