package net.momirealms.sparrow.feature;

import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.feature.back.BackFeature;
import net.momirealms.sparrow.feature.back.BackSettings;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerConnection;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackFeatureTest {
    private final BackSettings settings = new BackSettings();
    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final BukkitSparrowPlayer sparrow = mock(BukkitSparrowPlayer.class);
    private final PlayerConnection connection = mock(PlayerConnection.class);
    private final World world = mock(World.class);
    private SparrowPlugin plugin;
    private BackFeature back;

    @BeforeEach
    void setUp() {
        this.plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
        when(this.plugin.configurationManager().featuresConfig().config().back()).thenReturn(this.settings);
        when(this.plugin.playerManager().getPlayer(this.player)).thenReturn(this.sparrow);
        when(this.player.getUniqueId()).thenReturn(this.id);
        when(this.sparrow.uniqueId()).thenReturn(this.id);
        when(this.sparrow.connection()).thenReturn(this.connection);
        when(this.connection.connectedAt()).thenReturn(System.currentTimeMillis() - 60_000);
        when(this.world.getName()).thenReturn("world");
        this.back = new BackFeature(this.plugin);
        ((Feature<?>) this.back).install();
    }

    @Test
    void recordsTheStartOfConfiguredTeleportsOnly() {
        this.teleport(TeleportCause.COMMAND, this.at(0), this.at(100));
        assertEquals(WorldLocation.from(this.at(0)), this.back.point(this.id));
        // 末影珍珠不在默认列表里, 原地转视角也不算
        this.teleport(TeleportCause.ENDER_PEARL, this.at(100), this.at(200));
        this.teleport(TeleportCause.PLUGIN, this.at(100), this.at(100.5));
        assertEquals(WorldLocation.from(this.at(0)), this.back.point(this.id));
    }

    @Test
    void ignoresTeleportsBeforeJoiningAndDuringTheGracePeriod() throws Exception {
        when(this.plugin.playerManager().getPlayer(this.player)).thenReturn(null);
        this.teleport(TeleportCause.PLUGIN, this.at(0), this.at(100));
        assertNull(this.back.point(this.id));

        when(this.plugin.playerManager().getPlayer(this.player)).thenReturn(this.sparrow);
        when(this.connection.connectedAt()).thenReturn(System.currentTimeMillis());
        this.teleport(TeleportCause.PLUGIN, this.at(0), this.at(100));
        assertNull(this.back.point(this.id));

        set(this.settings, "joinGraceSeconds", 0);
        this.teleport(TeleportCause.PLUGIN, this.at(0), this.at(100));
        assertEquals(WorldLocation.from(this.at(0)), this.back.point(this.id));
    }

    @Test
    void recordsDeathUnlessDisabledAndForgetsOnQuit() throws Exception {
        when(this.player.getLocation()).thenReturn(this.at(42));
        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(this.player);
        this.back.onDeath(death);
        assertEquals(WorldLocation.from(this.at(42)), this.back.point(this.id));

        this.back.onQuit(this.sparrow);
        assertNull(this.back.point(this.id));

        set(this.settings, "recordDeath", false);
        this.back.onDeath(death);
        assertNull(this.back.point(this.id));
    }

    @Test
    void treatsOnlyRecentLogoutsFromOtherServersAsServerSwitches() {
        WorldLocation spot = WorldLocation.from(this.at(0));
        long now = System.currentTimeMillis();
        when(this.connection.connectedAt()).thenReturn(now);
        try (MockedStatic<ServerConfig> config = mockStatic(ServerConfig.class)) {
            config.when(ServerConfig::serverId).thenReturn("lobby");
            // 代理先进入新服务器再断开旧服务器, 下线时间略晚于进服时间
            assertTrue(this.back.switchedFrom(this.sparrow, this.data("survival", spot, now + 500)));
            assertFalse(this.back.switchedFrom(this.sparrow, this.data("survival", spot, now - 86_400_000L)));
            assertFalse(this.back.switchedFrom(this.sparrow, this.data("lobby", spot, now)));
            assertFalse(this.back.switchedFrom(this.sparrow, this.data("survival", null, now)));
        }
    }

    @Test
    void rejectsUnknownTeleportCauses() throws Exception {
        set(this.settings, "teleportCauses", List.of("COMMAND", "WARP"));
        BackFeature invalid = new BackFeature(this.plugin);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, invalid::loadConfig);
        assertTrue(error.getMessage().contains("WARP"));
    }

    private void teleport(TeleportCause cause, Location from, Location to) {
        this.back.onTeleport(new PlayerTeleportEvent(this.player, from, to, cause));
    }

    private Location at(double x) {
        return new Location(this.world, x, 64, 0);
    }

    private PlayerData data(String server, WorldLocation location, long lastLogout) {
        return new PlayerData(this.id, "Tester", lastLogout, lastLogout, server, location, null, lastLogout);
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
