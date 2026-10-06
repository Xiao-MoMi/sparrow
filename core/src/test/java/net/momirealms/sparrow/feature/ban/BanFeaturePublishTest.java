package net.momirealms.sparrow.feature.ban;

import io.netty.buffer.ByteBuf;
import net.momirealms.sparrow.database.BanStore;
import net.momirealms.sparrow.player.cluster.ClusterPlayer;
import net.momirealms.sparrow.player.cluster.ClusterRoster;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.util.IpRange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BanFeaturePublishTest {
    private final UUID player = UUID.randomUUID();
    private final BanTarget.PlayerTarget target = new BanTarget.PlayerTarget(this.player, "Steve");
    private final BanStore store = mock(BanStore.class);
    private final ClusterRoster cluster = mock(ClusterRoster.class);
    @SuppressWarnings("unchecked")
    private final MessageBroker<ByteBuf> broker = mock(MessageBroker.class);
    private SparrowPlugin plugin;
    private MockedStatic<ServerConfig> config;
    private BanFeature feature;

    @BeforeEach
    void setUp() {
        this.plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
        when(this.plugin.dataStorage().banStore()).thenReturn(this.store);
        when(this.plugin.messageBrokerManager().broker()).thenReturn(this.broker);
        when(this.plugin.playerManager().cluster()).thenReturn(this.cluster);
        when(this.store.saveBan(any())).thenReturn(CompletableFuture.completedFuture(false));
        BanRecord revoked = new BanRecord("AB12CD34", this.player, "Steve", null, "", "Admin", "survival", 0, 0, 0, null);
        when(this.store.revokeBans(any(), anyLong(), anyString())).thenReturn(CompletableFuture.completedFuture(List.of(revoked)));
        this.config = mockStatic(ServerConfig.class);
        this.config.when(ServerConfig::serverId).thenReturn("survival");
        this.feature = new BanFeature(this.plugin);
    }

    @AfterEach
    void tearDown() {
        this.config.close();
    }

    @Test
    void announcedBansReachEveryServer() {
        this.feature.ban(this.target, null, "", 0, "Admin", false).join();

        ArgumentCaptor<BanMessage> message = ArgumentCaptor.forClass(BanMessage.class);
        verify(this.broker).publishOneWay(message.capture(), eq(""));
        assertFalse(message.getValue().silent());
    }

    @Test
    void silentAccountBanOnlyReachesThePlayersServer() {
        when(this.cluster.find(this.player)).thenReturn(new ClusterPlayer(this.player, "Steve", "lobby"));

        this.feature.ban(this.target, null, "", 0, "Admin", true).join();

        ArgumentCaptor<BanMessage> message = ArgumentCaptor.forClass(BanMessage.class);
        verify(this.broker).publishOneWay(message.capture(), eq("lobby"));
        assertTrue(message.getValue().silent());
    }

    @Test
    void silentAccountBanOfOfflinePlayerSendsNothing() {
        this.feature.ban(this.target, null, "", 0, "Admin", true).join();

        verifyNoInteractions(this.broker);
    }

    @Test
    void silentIpBanStillReachesEveryServerToKick() {
        this.feature.ban(null, IpRange.parse("1.2.3.*"), "", 0, "Admin", true).join();

        verify(this.broker).publishOneWay(any(), eq(""));
    }

    @Test
    void silentUnbanSendsNothing() {
        this.feature.unban(this.target, "Admin", true).join();
        verifyNoInteractions(this.broker);

        this.feature.unban(this.target, "Admin", false).join();
        verify(this.broker).publishOneWay(any(), eq(""));
    }
}
