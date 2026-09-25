package net.momirealms.sparrow.feature.head;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.component.ResolvableProfile;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HeadItemsTest {
    @BeforeAll
    static void initializeMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void createsIndependentStacksWithResolvedProfilesAndExactTextures() {
        HeadData data = new HeadData(UUID.randomUUID(), "Tester", "texture", "signature");
        var first = ((CraftItemStack) HeadItems.create(data, 64)).handle;
        var second = ((CraftItemStack) HeadItems.create(data, 2)).handle;
        var profile = first.get(DataComponents.PROFILE);
        assertInstanceOf(ResolvableProfile.Static.class, profile);
        assertEquals(data.texture(), profile.partialProfile().properties().get("textures").iterator().next().value());
        first.setCount(1);
        assertEquals(2, second.getCount());
        assertNotSame(first, second);
    }
}
