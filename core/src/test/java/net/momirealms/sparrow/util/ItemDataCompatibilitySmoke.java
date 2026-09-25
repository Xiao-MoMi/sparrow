package net.momirealms.sparrow.util;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.Map;

public final class ItemDataCompatibilitySmoke {
    public static void main(String[] args) throws Exception {
        var output = System.out;
        var errors = System.err;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        System.setOut(output);
        System.setErr(errors);
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        if (!args[0].equals("1.21.11")) {
            Holder.Reference.class.getMethod("bindComponents", DataComponentMap.class).invoke(Items.STONE.builtInRegistryHolder(), DataComponents.COMMON_ITEM_COMPONENTS);
        }
        ItemStack item = new ItemStack(Items.STONE, 3);
        Map<String, Object> plain = ItemUtils.readableData(item, registries, false).getOrThrow();
        require("minecraft:stone".equals(plain.get("id")), "item id");
        require(((Number) plain.get("count")).intValue() == 3, "item count");
        require(!plain.containsKey("components"), "default patch is empty");

        CompoundTag custom = new CompoundTag();
        custom.putString("literal", "<red>text with ' quotes\nand newline");
        custom.putByteArray("bytes", new byte[]{1, -2});
        custom.putIntArray("ints", new int[]{3, 4});
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        item.remove(DataComponents.RARITY);
        var patch = item.getComponentsPatch();
        Map<?, ?> changed = (Map<?, ?>) ItemUtils.readableData(item, registries, false).getOrThrow().get("components");
        require(changed.containsKey("!minecraft:rarity"), "removed component marker");
        Map<?, ?> encodedCustom = (Map<?, ?>) changed.get("minecraft:custom_data");
        require("<red>text with ' quotes\nand newline".equals(encodedCustom.get("literal")), "literal custom data");
        require(encodedCustom.containsKey("bytes") && encodedCustom.containsKey("ints"), "NBT arrays");

        Map<String, Object> full = ItemUtils.readableData(item, registries, true).getOrThrow();
        Map<?, ?> components = (Map<?, ?>) full.get("components");
        require(components.containsKey("minecraft:max_stack_size"), "full includes defaults");
        require(components.containsKey("minecraft:custom_data"), "full includes custom data");
        require(!components.containsKey("minecraft:rarity") && !components.containsKey("!minecraft:rarity"), "full contains only effective components");
        require(patch.equals(item.getComponentsPatch()), "serialization does not change item");
        require(DataTreeRenderer.render(full, true).size() > 10, "full tree rendering");
        System.out.println("PASS item data codec, full components, removal, NBT and rendering: " + args[0]);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
