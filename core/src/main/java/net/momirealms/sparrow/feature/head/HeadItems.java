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


final class HeadItems {
    private static final String TEXTURES = "textures";

    private HeadItems() {
    }

    static HeadData fromProfile(GameProfile profile) {
        var textures = profile.properties().get(TEXTURES);
        if (textures.isEmpty()) return null;
        Property texture = textures.iterator().next();
        return new HeadData(profile.id(), profile.name(), texture.value(), texture.signature());
    }

    // 完整的静态 Profile 随物品保存, 客户端使用本次取得的纹理.
    static org.bukkit.inventory.ItemStack create(HeadData data, int amount) {
        Property property = new Property(TEXTURES, data.texture(), data.signature());
        GameProfile profile = new GameProfile(data.uuid(), data.name(), new PropertyMap(ImmutableMultimap.of(TEXTURES, property)));
        ItemStack item = new ItemStack(Items.PLAYER_HEAD, amount);
        item.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return CraftItemStackProxy.INSTANCE.asBukkitMirror(item);
    }
}
