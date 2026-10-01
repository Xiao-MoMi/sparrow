package net.momirealms.sparrow.player.teleport;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeleportServiceTest {
    private static final WorldLocation DESTINATION = new WorldLocation("world", 100, 64, 100, 0, 0);

    private final SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
    private final TeleportManager teleports = mock(TeleportManager.class);
    private final BukkitSparrowPlayer receiver = mock(BukkitSparrowPlayer.class);
    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    @SuppressWarnings("unchecked")
    private final RedisAsyncCommands<byte[], byte[]> redis = mock(RedisAsyncCommands.class);
    private final List<Runnable> ticking = new ArrayList<>();
    private final SchedulerTask task = mock(SchedulerTask.class);
    private final PluginConfig.TeleportDisplay display = new PluginConfig.TeleportDisplay();
    private Location location;
    private MockedStatic<PluginConfig> pluginConfig;
    private TeleportService service;

    @BeforeEach
    void setUp() {
        this.location = new Location(this.world, 0, 64, 0);
        when(this.player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(this.player.getLocation()).thenAnswer(invocation -> this.location.clone());
        when(this.plugin.playerManager().getPlayer(this.player)).thenReturn(this.receiver);
        when(this.receiver.platformPlayer()).thenReturn(this.player);
        when(this.receiver.uniqueId()).thenAnswer(invocation -> this.player.getUniqueId());
        when(this.plugin.redisConnector().connection().async()).thenReturn(this.redis);
        PlatformExecutor platform = this.plugin.scheduler().platform();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(platform).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
        when(platform.runRepeating(any(Runnable.class), any(Runnable.class), anyLong(), anyLong(), any(Entity.class))).thenAnswer(invocation -> {
            this.ticking.add(invocation.getArgument(0));
            return this.task;
        });
        doAnswer(invocation -> {
            this.ticking.clear();
            return null;
        }).when(this.task).cancel();
        when(this.teleports.transfer(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(TransferResult.SUCCESS));
        this.service = new TeleportService(this.plugin, this.teleports);
        // 静态模拟放在最后创建, 前面的配置出错时不会遗留给其他测试
        this.pluginConfig = mockStatic(PluginConfig.class);
        this.pluginConfig.when(PluginConfig::teleport).thenReturn(this.display);
    }

    @AfterEach
    void tearDown() {
        this.pluginConfig.close();
    }

    @Test
    void teleportsAfterWarmupCountdown() {
        CompletableFuture<TeleportResult> result = this.service.teleport(this.player, "lobby", DESTINATION, options(1, 0, true, true));
        verify(this.receiver).sendActionBar(same(MessageConstants.TELEPORT_WARMUP), any(Component[].class));
        verify(this.receiver).playSound(this.display.warmupSound());
        this.tick(19);
        assertFalse(result.isDone());
        // 小幅晃动不会取消
        this.location = new Location(this.world, 0.3, 64, 0.3);
        this.tick(1);
        assertEquals(TeleportResult.SUCCESS, result.join());
        // 先把倒计时换成正在传送再传送
        InOrder order = inOrder(this.receiver, this.teleports);
        order.verify(this.receiver).sendActionBar(same(MessageConstants.TELEPORT_PROCESSING), any(Component[].class));
        order.verify(this.teleports).transfer(this.player, "lobby", DESTINATION);
        verify(this.receiver).playSound(this.display.completeSound());
        verify(this.task).cancel();
    }

    @Test
    void clearsTitleCountdownWhenFinished() throws ReflectiveOperationException {
        Field display = PluginConfig.TeleportDisplay.class.getDeclaredField("warmupDisplay");
        display.setAccessible(true);
        display.set(this.display, PluginConfig.WarmupDisplay.TITLE);
        CompletableFuture<TeleportResult> result = this.service.teleport(this.player, "lobby", DESTINATION, options(3, 0, true, true));
        this.location = new Location(this.world, 1, 64, 0);
        this.tick(1);
        assertEquals(TeleportResult.CANCELLED, result.join());
        verify(this.receiver).clearTitle();
        verify(this.receiver, never()).sendActionBar(any(Component.class));
    }

    @Test
    void cancelsWhenMovingOrDamaged() {
        CompletableFuture<TeleportResult> moved = this.service.teleport(this.player, "lobby", DESTINATION, options(3, 0, true, true));
        this.location = new Location(this.world, 1, 64, 0);
        this.tick(1);
        assertEquals(TeleportResult.CANCELLED, moved.join());
        verify(this.receiver).sendMessage(same(MessageConstants.TELEPORT_CANCELLED_MOVED), any(Component[].class));
        // 取消时清掉倒计时
        verify(this.receiver).sendActionBar(Component.empty());
        verify(this.receiver).playSound(this.display.cancelSound());

        CompletableFuture<TeleportResult> damaged = this.service.teleport(this.player, "lobby", DESTINATION, options(3, 0, true, true));
        this.service.onDamage(this.damage());
        assertEquals(TeleportResult.CANCELLED, damaged.join());
        verify(this.receiver).sendMessage(same(MessageConstants.TELEPORT_CANCELLED_DAMAGED), any(Component[].class));
        verify(this.teleports, never()).transfer(any(), any(), any());
    }

    @Test
    void ignoresMovementAndDamageWhenDisabled() {
        CompletableFuture<TeleportResult> result = this.service.teleport(this.player, "lobby", DESTINATION, options(1, 0, false, false));
        this.location = new Location(this.world, 5, 64, 0);
        this.service.onDamage(this.damage());
        this.tick(20);
        assertEquals(TeleportResult.SUCCESS, result.join());
    }

    @Test
    void replacedOrQuitWarmupsCancelSilently() {
        CompletableFuture<TeleportResult> first = this.service.teleport(this.player, "lobby", DESTINATION, options(3, 0, true, true));
        CompletableFuture<TeleportResult> second = this.service.teleport(this.player, "lobby", DESTINATION, options(3, 0, true, true));
        assertEquals(TeleportResult.CANCELLED, first.join());
        assertFalse(second.isDone());
        this.service.onQuit(this.player.getUniqueId());
        assertEquals(TeleportResult.CANCELLED, second.join());
        verify(this.receiver, never()).sendMessage(any(TranslatableComponent.Builder.class), any(Component[].class));
        // 已取消的预热不再响应伤害
        this.service.onDamage(this.damage());
        verify(this.receiver, never()).sendMessage(any(TranslatableComponent.Builder.class), any(Component[].class));
    }

    @Test
    void checksAndRecordsCooldownInRedis() {
        RedisFuture<Long> remaining = redisFuture(1500L);
        when(this.redis.pttl(any())).thenReturn(remaining);
        assertEquals(TeleportResult.COOLDOWN, this.service.teleport(this.player, "lobby", DESTINATION, options(0, 10, true, true)).join());
        verify(this.receiver).sendMessage(same(MessageConstants.TELEPORT_COOLDOWN), any(Component[].class));
        verify(this.teleports, never()).transfer(any(), any(), any());
        // 没有冷却记录时传送, 成功后写入带过期时间的键
        RedisFuture<Long> expired = redisFuture(-2L);
        when(this.redis.pttl(any())).thenReturn(expired);
        assertEquals(TeleportResult.SUCCESS, this.service.teleport(this.player, "lobby", DESTINATION, options(0, 10, true, true)).join());
        verify(this.redis).set(argThat(key -> new String(key).equals("sparrow:teleport-cooldown:warp:" + this.player.getUniqueId())), any(), any(SetArgs.class));
        // 冷却为 0 时不访问 Redis
        this.service.teleport(this.player, "lobby", DESTINATION, options(0, 0, true, true)).join();
        verify(this.redis, times(2)).pttl(any());
        verify(this.redis, times(1)).set(any(), any(), any(SetArgs.class));
    }

    @Test
    void failedTransfersDoNotStartCooldown() {
        RedisFuture<Long> remaining = redisFuture(-2L);
        when(this.redis.pttl(any())).thenReturn(remaining);
        when(this.teleports.transfer(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(TransferResult.SERVER_OFFLINE));
        assertEquals(TeleportResult.SERVER_OFFLINE, this.service.teleport(this.player, "lobby", DESTINATION, options(0, 10, true, true)).join());
        verify(this.redis, never()).set(any(), any(), any(SetArgs.class));
    }

    private void tick(int times) {
        for (int i = 0; i < times; i++) {
            for (Runnable runnable : List.copyOf(this.ticking)) runnable.run();
        }
    }

    private EntityDamageEvent damage() {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getEntity()).thenReturn(this.player);
        return event;
    }

    private static TeleportOptions options(int warmup, int cooldown, boolean cancelOnMove, boolean cancelOnDamage) {
        return new TeleportOptions(TeleportType.WARP, warmup, cooldown, cancelOnMove, cancelOnDamage);
    }

    @SuppressWarnings("unchecked")
    private static RedisFuture<Long> redisFuture(long value) {
        RedisFuture<Long> future = mock(RedisFuture.class);
        when(future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(value));
        return future;
    }
}
