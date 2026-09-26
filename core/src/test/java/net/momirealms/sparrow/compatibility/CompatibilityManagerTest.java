package net.momirealms.sparrow.compatibility;

import net.kyori.adventure.util.TriState;
import net.momirealms.sparrow.compatibility.luckperms.LuckPermsHook;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompatibilityManagerTest {
    private static final String PERMISSION = "sparrow.bypass.test";

    private final CompatibilityManager manager = new CompatibilityManager(mock(SparrowPlugin.class));
    private final UUID player = UUID.randomUUID();
    private final OfflinePlayer offline = mock(OfflinePlayer.class);
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() {
        this.bukkit = mockStatic(Bukkit.class);
        this.bukkit.when(() -> Bukkit.getOfflinePlayer(this.player)).thenReturn(this.offline);
    }

    @AfterEach
    void tearDown() {
        this.bukkit.close();
    }

    @Test
    void withoutPermissionPluginOnlyOperatorsHavePermission() {
        assertFalse(this.manager.hasPermissionBeforeJoin(this.player, PERMISSION));
        when(this.offline.isOp()).thenReturn(true);
        assertTrue(this.manager.hasPermissionBeforeJoin(this.player, PERMISSION));
    }

    @Test
    void luckPermsResultOverridesOperatorStatus() throws Exception {
        LuckPermsHook hook = mock(LuckPermsHook.class);
        Field field = CompatibilityManager.class.getDeclaredField("luckPerms");
        field.setAccessible(true);
        field.set(this.manager, hook);

        when(hook.check(this.player, PERMISSION)).thenReturn(TriState.TRUE);
        assertTrue(this.manager.hasPermissionBeforeJoin(this.player, PERMISSION));
        // 显式拒绝的 OP 也没有权限
        when(this.offline.isOp()).thenReturn(true);
        when(hook.check(this.player, PERMISSION)).thenReturn(TriState.FALSE);
        assertFalse(this.manager.hasPermissionBeforeJoin(this.player, PERMISSION));
        // 未设置时按 OP 判断
        when(hook.check(this.player, PERMISSION)).thenReturn(TriState.NOT_SET);
        assertTrue(this.manager.hasPermissionBeforeJoin(this.player, PERMISSION));
    }
}
