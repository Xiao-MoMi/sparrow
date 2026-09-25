package net.momirealms.sparrow.bukkit.util;

import net.momirealms.sparrow.bukkit.SparrowBukkitPlugin;
import net.momirealms.sparrow.common.feature.skull.SkullData;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ItemStackUtils {
    private ItemStackUtils() {
    }

    public static void applySkull(@NotNull ItemStack skull, @Nullable SkullData skullData) {
        try {
            SparrowBukkitPlugin.getInstance().getItemFactory().wrap(skull)
                    .skull(skullData)
                    .load();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
