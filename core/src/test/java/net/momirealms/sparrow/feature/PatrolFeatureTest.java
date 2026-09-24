package net.momirealms.sparrow.feature;

import net.momirealms.sparrow.feature.patrol.PatrolFeature;
import net.momirealms.sparrow.feature.patrol.PatrolSettings;
import net.momirealms.sparrow.plugin.configuration.FeaturesConfig;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatrolFeatureTest {
    private final PatrolSettings settings = new PatrolSettings();
    private PatrolFeature patrol;
    private Player admin;

    @BeforeEach
    void setUp() {
        FeaturesConfig config = mock(FeaturesConfig.class, RETURNS_DEEP_STUBS);
        when(config.config().patrol()).thenReturn(this.settings);
        this.patrol = new PatrolFeature(mock(JavaPlugin.class, RETURNS_DEEP_STUBS), config);
        ((Feature<?>) this.patrol).install();
        this.admin = this.player("admin");
    }

    @Test
    void rotatesThroughEveryCandidateBeforeRepeating() {
        List<Player> candidates = List.of(this.admin, this.player("a"), this.player("b"), this.player("c"));
        // 一轮内每名玩家各被选中一次, 巡查者自己不会被选中
        Set<Player> round = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            round.add(this.patrol.claimNext(this.admin, candidates));
        }
        assertEquals(Set.copyOf(candidates.subList(1, 4)), round);
        // 第二轮按第一轮的先后顺序重复
        List<Player> first = new ArrayList<>();
        List<Player> second = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            first.add(this.patrol.claimNext(this.admin, candidates));
        }
        for (int i = 0; i < 3; i++) {
            second.add(this.patrol.claimNext(this.admin, candidates));
        }
        assertEquals(first, second);
    }

    @Test
    void newPlayersAndRejoinedPlayersComeFirst() {
        Player a = this.player("a");
        Player b = this.player("b");
        this.patrol.claimNext(this.admin, List.of(a, b));
        Player patrolled = this.patrol.claimNext(this.admin, List.of(a, b));
        // 刚进服的玩家从未被巡查, 排在已巡查的玩家之前
        Player joined = this.player("joined");
        assertSame(joined, this.patrol.claimNext(this.admin, List.of(a, b, joined)));
        // 重新进服的玩家回到队首
        this.quit(patrolled);
        this.join(patrolled);
        assertSame(patrolled, this.patrol.claimNext(this.admin, List.of(a, b, joined)));
    }

    @Test
    void skipsBypassSpectatorsExcludedWorldsAndOfflinePlayers() throws Exception {
        Player bypass = this.player("bypass");
        when(bypass.hasPermission(PatrolFeature.BYPASS_PERMISSION)).thenReturn(true);
        Player spectator = this.player("spectator");
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        Player nether = this.player("nether");
        when(nether.getWorld().getName()).thenReturn("world_nether");
        Player offline = this.player("offline");
        when(offline.isOnline()).thenReturn(false);
        set(this.settings, "excludedWorlds", List.of("world_nether"));
        List<Player> candidates = List.of(bypass, spectator, nether, offline);
        assertNull(this.patrol.claimNext(this.admin, candidates));
        // 关闭旁观者筛选后旁观玩家可以被选中
        set(this.settings, "skipSpectators", false);
        assertSame(spectator, this.patrol.claimNext(this.admin, candidates));
    }

    @Test
    void quitPlayersLeaveQueue() {
        Player a = this.player("a");
        this.quit(a);
        assertNull(this.patrol.claimNext(this.admin, List.of(a)));
    }

    // 创建玩家并触发进服事件
    private Player player(String name) {
        Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(player.getWorld()).thenReturn(world);
        this.join(player);
        return player;
    }

    private void join(Player player) {
        PlayerJoinEvent event = mock(PlayerJoinEvent.class);
        when(event.getPlayer()).thenReturn(player);
        this.patrol.onJoin(event);
    }

    private void quit(Player player) {
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        this.patrol.onQuit(event);
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
