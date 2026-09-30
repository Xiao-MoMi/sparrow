package net.momirealms.sparrow.compatibility;

import net.momirealms.sparrow.compatibility.luckperms.LuckPermsHook;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionLimitTest {
    private static final String NODE = "sparrow.max-homes";

    private final CompatibilityManager compatibility = new CompatibilityManager(mock(SparrowPlugin.class));
    private final Player player = mock(Player.class);

    @Test
    void usesTheDefaultWithoutNumericNodes() {
        this.grant();
        assertEquals(5, this.compatibility.permissionLimit(this.player, NODE, 5));
    }

    @Test
    void takesTheHighestNodeEvenBelowTheDefault() {
        this.grant("sparrow.max-homes.3", "sparrow.max-homes.7");
        assertEquals(7, this.compatibility.permissionLimit(this.player, NODE, 10));
        this.grant("sparrow.max-homes.2");
        assertEquals(2, this.compatibility.permissionLimit(this.player, NODE, 10));
    }

    @Test
    void minimumTakesTheLowestNodeEvenAboveTheDefault() {
        this.grant("sparrow.teleport-warmup.5", "sparrow.teleport-warmup.2");
        assertEquals(2, this.compatibility.permissionMinimum(this.player, "sparrow.teleport-warmup", 3));
        this.grant("sparrow.teleport-warmup.10");
        assertEquals(10, this.compatibility.permissionMinimum(this.player, "sparrow.teleport-warmup", 3));
        this.grant("sparrow.teleport-warmup.0", "sparrow.teleport-warmup.abc");
        assertEquals(0, this.compatibility.permissionMinimum(this.player, "sparrow.teleport-warmup", 3));
        this.grant();
        assertEquals(3, this.compatibility.permissionMinimum(this.player, "sparrow.teleport-warmup", 3));
    }

    @Test
    void unlimitedWinsOverNumericNodes() {
        this.grant("sparrow.max-homes.3");
        when(this.player.hasPermission(NODE + ".unlimited")).thenReturn(true);
        assertEquals(CompatibilityManager.UNLIMITED, this.compatibility.permissionLimit(this.player, NODE, 5));
    }

    @Test
    void ignoresMalformedNegatedAndUnrelatedNodes() {
        Set<PermissionAttachmentInfo> infos = new HashSet<>();
        infos.add(new PermissionAttachmentInfo(this.player, "sparrow.max-homes.9", null, false));
        infos.add(new PermissionAttachmentInfo(this.player, "sparrow.max-homes.abc", null, true));
        infos.add(new PermissionAttachmentInfo(this.player, "sparrow.max-homes.-1", null, true));
        infos.add(new PermissionAttachmentInfo(this.player, "sparrow.max-homes.", null, true));
        infos.add(new PermissionAttachmentInfo(this.player, "sparrow.max-homes.99999999999", null, true));
        infos.add(new PermissionAttachmentInfo(this.player, "sparrow.max-homes-public.8", null, true));
        infos.add(new PermissionAttachmentInfo(this.player, "sparrow.max-homes.4.extra", null, true));
        when(this.player.getEffectivePermissions()).thenReturn(infos);
        assertEquals(5, this.compatibility.permissionLimit(this.player, NODE, 5));
    }

    @Test
    void readsLuckPermsWhenHooked() throws Exception {
        LuckPermsHook hook = mock(LuckPermsHook.class);
        when(hook.permissionMap(this.player)).thenReturn(Map.of("sparrow.max-homes.4", true, "sparrow.max-homes.12", false, "sparrow.max-homes.6", true));
        Field field = CompatibilityManager.class.getDeclaredField("luckPerms");
        field.setAccessible(true);
        field.set(this.compatibility, hook);
        assertEquals(6, this.compatibility.permissionLimit(this.player, NODE, 1));
        verify(this.player, never()).getEffectivePermissions();
    }

    private void grant(String... permissions) {
        Set<PermissionAttachmentInfo> infos = new HashSet<>();
        for (String permission : permissions) {
            infos.add(new PermissionAttachmentInfo(this.player, permission, null, true));
        }
        when(this.player.getEffectivePermissions()).thenReturn(infos);
    }
}
