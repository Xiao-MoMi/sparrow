package net.momirealms.sparrow.bukkit.feature.item;

import com.saicone.rtag.RtagItem;
import net.momirealms.sparrow.bukkit.SparrowBukkitPlugin;
import net.momirealms.sparrow.bukkit.feature.item.impl.ComponentItemFactory1_21_4;
import net.momirealms.sparrow.bukkit.feature.item.impl.ComponentItemFactory1_21_5;
import net.momirealms.sparrow.bukkit.feature.item.impl.ComponentItemFactory;
import net.momirealms.sparrow.bukkit.feature.item.impl.UniversalItemFactory;
import net.momirealms.sparrow.common.feature.item.ItemFactory;
import net.momirealms.sparrow.common.feature.item.SparrowItem;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public abstract class SparrowBukkitItemFactory extends ItemFactory<SparrowBukkitPlugin, RtagItem, ItemStack> {

    protected SparrowBukkitItemFactory(SparrowBukkitPlugin plugin) {
        super(plugin);
    }

    public static SparrowBukkitItemFactory create(SparrowBukkitPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        switch (plugin.getBootstrap().getServerVersion()) {
            case "1.17", "1.17.1",
                 "1.18", "1.18.1", "1.18.2",
                 "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
                 "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4" -> {
                return new UniversalItemFactory(plugin);
            }
            case "1.20.5", "1.20.6",
                 "1.21", "1.21.1", "1.21.2", "1.21.3" -> {
                return new ComponentItemFactory(plugin);
            }
            case "1.21.4" -> {
                return new ComponentItemFactory1_21_4(plugin);
            }
            default -> {
                return new ComponentItemFactory1_21_5(plugin);
            }
        }
    }

    public SparrowItem<ItemStack> wrap(ItemStack item) {
        Objects.requireNonNull(item, "item");
        return wrap(new RtagItem(item));
    }

    @Override
    protected void update(RtagItem item) {
        item.update();
    }

    @Override
    protected ItemStack load(RtagItem item) {
        return item.load();
    }

    @Override
    protected ItemStack getItem(RtagItem item) {
        return item.getItem();
    }

    @Override
    protected ItemStack loadCopy(RtagItem item) {
        return item.loadCopy();
    }
}
