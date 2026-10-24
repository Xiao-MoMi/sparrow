package net.momirealms.sparrow.feature.head;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.momirealms.sparrow.proxy.bukkit.inventory.CraftItemStackProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class HeadItems {

    private HeadItems() {
    }

    @Nullable
    static HeadData fromProfile(@NotNull GameProfile profile) {
        for (Property property : profile.properties().get("textures")) {
            return new HeadData(profile.id(), profile.name(), property.value(), property.signature());
        }
        return null;
    }

    // 完整的静态 Profile 随物品保存, 客户端使用本次取得的纹理.
    @NotNull
    static org.bukkit.inventory.ItemStack create(@NotNull HeadData data, int amount) {
        Property property = new Property("textures", data.texture(), data.signature());
        GameProfile profile = new GameProfile(data.uuid(), data.name(), new PropertyMap(ImmutableMultimap.of("textures", property)));
        ItemStack item = new ItemStack(Items.PLAYER_HEAD, amount);
        item.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return CraftItemStackProxy.INSTANCE.asBukkitMirror(item);
    }
}