package net.momirealms.sparrow.feature.playerlimit;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.event.player.PlayerServerFullCheckEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLoginEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
class PlayerLimitListenerTest {
    private final UUID uniqueId = UUID.randomUUID();
    private PlayerLimitFeature feature;

    @BeforeEach
    void setUp() {
        this.feature = mock(PlayerLimitFeature.class);
        when(this.feature.enabled()).thenReturn(true);
    }

    @Test
    void paperAllowsFullServerWithBypassPermission() {
        PaperPlayerLimitListener listener = new PaperPlayerLimitListener(this.feature);
        when(this.feature.bypassBeforeJoin(this.uniqueId)).thenReturn(true);
        PlayerServerFullCheckEvent event = this.fullCheck(true);
        listener.onFullCheck(event);
        assertTrue(event.isAllowed());
    }

    @Test
    void paperKeepsFullServerWithoutPermissionOrWhenDisabled() {
        PaperPlayerLimitListener listener = new PaperPlayerLimitListener(this.feature);
        PlayerServerFullCheckEvent denied = this.fullCheck(true);
        listener.onFullCheck(denied);
        assertFalse(denied.isAllowed());

        when(this.feature.bypassBeforeJoin(this.uniqueId)).thenReturn(true);
        when(this.feature.enabled()).thenReturn(false);
        PlayerServerFullCheckEvent disabled = this.fullCheck(true);
        listener.onFullCheck(disabled);
        assertFalse(disabled.isAllowed());
    }

    @Test
    void paperSkipsPermissionLookupWhenNotFull() {
        PaperPlayerLimitListener listener = new PaperPlayerLimitListener(this.feature);
        PlayerServerFullCheckEvent event = this.fullCheck(false);
        listener.onFullCheck(event);
        assertTrue(event.isAllowed());
        verify(this.feature, never()).bypassBeforeJoin(any());
    }

    @Test
    void spigotAllowsOnlyFullServerKicks() {
        SpigotPlayerLimitListener listener = new SpigotPlayerLimitListener(this.feature);
        Player player = mock(Player.class);
        when(player.hasPermission(PlayerLimitFeature.BYPASS_PERMISSION)).thenReturn(true);

        PlayerLoginEvent full = new PlayerLoginEvent(player, "localhost", InetAddress.getLoopbackAddress());
        full.disallow(PlayerLoginEvent.Result.KICK_FULL, "full");
        listener.onLogin(full);
        assertEquals(PlayerLoginEvent.Result.ALLOWED, full.getResult());

        // 其他拒绝原因不受影响
        PlayerLoginEvent banned = new PlayerLoginEvent(player, "localhost", InetAddress.getLoopbackAddress());
        banned.disallow(PlayerLoginEvent.Result.KICK_BANNED, "banned");
        listener.onLogin(banned);
        assertEquals(PlayerLoginEvent.Result.KICK_BANNED, banned.getResult());

        PlayerLoginEvent noPermission = new PlayerLoginEvent(mock(Player.class), "localhost", InetAddress.getLoopbackAddress());
        noPermission.disallow(PlayerLoginEvent.Result.KICK_FULL, "full");
        listener.onLogin(noPermission);
        assertEquals(PlayerLoginEvent.Result.KICK_FULL, noPermission.getResult());
    }

    private PlayerServerFullCheckEvent fullCheck(boolean full) {
        PlayerProfile profile = mock(PlayerProfile.class);
        when(profile.getId()).thenReturn(this.uniqueId);
        return new PlayerServerFullCheckEvent(profile, Component.empty(), full);
    }
}
