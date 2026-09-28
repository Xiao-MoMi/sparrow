package net.momirealms.sparrow.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.momirealms.sparrow.feature.maintenance.MaintenanceFeature;
import net.momirealms.sparrow.feature.maintenance.MaintenanceSettings;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.FeaturesConfig;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("deprecation")
class MaintenanceFeatureTest {
    private static final Component KICK = Component.text("maintenance.kick");

    private final MaintenanceSettings settings = new MaintenanceSettings();
    private final List<SparrowPlayer> online = new ArrayList<>();
    private SparrowPlugin plugin;
    private FeaturesConfig featuresConfig;
    private MockedStatic<Bukkit> bukkit;
    private MaintenanceFeature feature;

    @BeforeEach
    void setUp() {
        this.plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
        this.featuresConfig = mock(FeaturesConfig.class, RETURNS_DEEP_STUBS);
        when(this.plugin.configurationManager().featuresConfig()).thenReturn(this.featuresConfig);
        when(this.featuresConfig.config().maintenance()).thenReturn(this.settings);
        doAnswer(invocation -> {
            this.settings.active(invocation.getArgument(0));
            return null;
        }).when(this.featuresConfig).saveMaintenanceActive(anyBoolean());
        when(this.plugin.playerManager().getOnlinePlayers()).thenAnswer(invocation -> List.copyOf(this.online));
        // 调度直接在当前线程执行
        PlatformExecutor executor = mock(PlatformExecutor.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
        when(this.plugin.scheduler().platform()).thenReturn(executor);

        // 渲染结果为翻译键本身
        TranslationManager manager = mock(TranslationManager.class);
        when(manager.render(any(TranslatableComponent.Builder.class), any()))
                .thenAnswer(invocation -> Component.text(((TranslatableComponent) invocation.<TranslatableComponent.Builder>getArgument(0).asComponent()).key()));
        when(this.plugin.translationManager()).thenReturn(manager);

        this.bukkit = mockStatic(Bukkit.class);
        this.bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

        this.feature = new MaintenanceFeature(this.plugin);
        ((Feature<?>) this.feature).install();
    }

    @AfterEach
    void tearDown() {
        this.bukkit.close();
    }

    @Test
    void registersAndUnregistersPlayerListener() {
        verify(this.plugin.playerManager()).registerListener(this.feature);
        ((Feature<?>) this.feature).uninstall();
        verify(this.plugin.playerManager()).unregisterListener(this.feature);
    }

    @Test
    void inactiveMaintenanceIgnoresLogins() {
        AsyncPlayerPreLoginEvent event = this.preLogin("Steve");
        this.feature.onPreLogin(event);
        verify(event, never()).disallow(any(AsyncPlayerPreLoginEvent.Result.class), anyString());
    }

    @Test
    void allowsLoginWithBypassPermission() {
        this.feature.active(true);
        AsyncPlayerPreLoginEvent granted = this.preLogin("Granted");
        when(this.plugin.compatibilityManager().hasPermissionBeforeJoin(granted.getUniqueId(), MaintenanceFeature.BYPASS_PERMISSION)).thenReturn(true);
        this.feature.onPreLogin(granted);
        verify(granted, never()).disallow(any(AsyncPlayerPreLoginEvent.Result.class), anyString());
    }

    @Test
    void skipsLoginsAlreadyDenied() {
        this.feature.active(true);
        AsyncPlayerPreLoginEvent event = this.preLogin("Steve");
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
        this.feature.onPreLogin(event);
        verify(event, never()).disallow(any(AsyncPlayerPreLoginEvent.Result.class), anyString());
    }

    @Test
    void togglingKicksOnlinePlayersAndManagesBossBars() {
        SparrowPlayer admin = this.player("admin", true);
        SparrowPlayer guest = this.player("guest", false);
        this.online.addAll(List.of(admin, guest));
        this.feature.active(true);
        verify(this.featuresConfig).saveMaintenanceActive(true);
        verify(guest).kick(KICK);
        verify(admin, never()).kick(any());
        assertEquals(List.of("add"), this.operations(admin));
        assertEquals(List.of(), this.operations(guest));
        // 重复开启不会再次发送
        this.feature.active(true);
        assertEquals(List.of("add"), this.operations(admin));

        this.feature.active(false);
        assertFalse(this.feature.active());
        assertEquals(List.of("add", "remove"), this.operations(admin));
    }

    @Test
    void joiningPlayersAreCheckedAgain() {
        this.feature.active(true);
        SparrowPlayer guest = this.player("guest", false);
        this.feature.onJoin(guest);
        verify(guest).kick(KICK);
        SparrowPlayer admin = this.player("admin", true);
        this.feature.onJoin(admin);
        assertEquals(List.of("add"), this.operations(admin));
        // 退出后不再发送移除包, 重新进入时再次显示
        this.feature.onQuit(admin);
        this.feature.onJoin(admin);
        assertEquals(List.of("add", "add"), this.operations(admin));
    }

    @Test
    void joiningDoesNothingWhenInactive() {
        SparrowPlayer guest = this.player("guest", false);
        this.feature.onJoin(guest);
        verify(guest, never()).kick(any());
        assertEquals(List.of(), this.operations(guest));
    }

    @Test
    void disabledBossBarSendsNothing() throws Exception {
        this.set(this.settings.bossBar(), "enabled", false);
        SparrowPlayer admin = this.player("admin", true);
        this.online.add(admin);
        this.feature.active(true);
        assertEquals(List.of(), this.operations(admin));
    }

    @Test
    void savedStateIsRestoredOnEnable() {
        SparrowPlayer guest = this.player("guest", false);
        this.online.add(guest);
        this.settings.active(true);
        ((Feature<?>) this.feature).stop();
        ((Feature<?>) this.feature).start();
        assertTrue(this.feature.active());
        verify(guest).kick(KICK);
    }

    @Test
    void rejectsUnknownBossBarColor() throws Exception {
        this.set(this.settings.bossBar(), "color", "ORANGE");
        assertThrows(IllegalArgumentException.class, this.feature::loadConfig);
    }

    private AsyncPlayerPreLoginEvent preLogin(String name) {
        AsyncPlayerPreLoginEvent event = mock(AsyncPlayerPreLoginEvent.class);
        when(event.getName()).thenReturn(name);
        when(event.getUniqueId()).thenReturn(UUID.randomUUID());
        when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        return event;
    }

    private SparrowPlayer player(String name, boolean bypass) {
        SparrowPlayer player = mock(SparrowPlayer.class);
        when(player.name()).thenReturn(name);
        when(player.uniqueId()).thenReturn(UUID.randomUUID());
        when(player.locale()).thenReturn(Locale.ROOT);
        when(player.hasPermission(MaintenanceFeature.BYPASS_PERMISSION)).thenReturn(bypass);
        ServerPlayer handle = mock(ServerPlayer.class);
        when(handle.getBukkitEntity()).thenReturn(mock(CraftPlayer.class));
        when(player.nmsPlayer()).thenReturn(handle);
        return player;
    }

    // 按调用顺序列出该玩家收到的 BossBar 操作
    private List<String> operations(SparrowPlayer player) {
        List<String> operations = new ArrayList<>();
        for (Invocation invocation : mockingDetails(player).getInvocations()) {
            String method = invocation.getMethod().getName();
            if (method.equals("showBossBar")) operations.add("add");
            if (method.equals("hideBossBar")) operations.add("remove");
        }
        return operations;
    }

    private void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
