package net.momirealms.sparrow.util;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.momirealms.sparrow.nbt.CompoundTag;
import net.momirealms.sparrow.nbt.Tag;
import net.momirealms.sparrow.nbt.codec.NBTOps;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ItemUtils {

    private ItemUtils() {}

    @NotNull
    public static CompoundTag toNbt(@NotNull ItemStack item) {
        return (CompoundTag) ItemStack.CODEC.encodeStart(NbtOpsHolder.OPS, item)
                .getOrThrow(message -> new IllegalStateException("Failed to encode item: " + message));
    }

    @NotNull
    public static ItemStack fromNbt(@NotNull CompoundTag tag) {
        return ItemStack.CODEC.parse(NbtOpsHolder.OPS, tag)
                .getOrThrow(message -> new IllegalStateException("Failed to decode item: " + message));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public static DataResult<Map<String, Object>> readableData(@NotNull ItemStack item, @NotNull HolderLookup.Provider registries, boolean full) {
        DynamicOps<Object> ops = registries.createSerializationContext(JavaOps.INSTANCE);
        return ItemStack.CODEC.encodeStart(ops, item).flatMap(encoded -> {
            Map<String, Object> data = new LinkedHashMap<>((Map<String, Object>) encoded);
            if (!full) {
                return DataResult.success(data);
            }
            return DataComponentMap.CODEC.encodeStart(ops, item.getComponents()).map(components -> {
                data.put("components", components);
                return data;
            });
        });
    }

    private static final class NbtOpsHolder {
        private static final RegistryOps<Tag> OPS = RegistryOps.create(NBTOps.INSTANCE, MinecraftServer.getServer().registryAccess());
    }
}