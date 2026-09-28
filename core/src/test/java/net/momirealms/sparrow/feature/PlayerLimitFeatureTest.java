package net.momirealms.sparrow.feature;

import net.momirealms.sparrow.feature.playerlimit.PlayerLimitFeature;
import net.momirealms.sparrow.feature.playerlimit.PlayerLimitSettings;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.FeaturesConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerLimitFeatureTest {
    private final PlayerLimitSettings settings = new PlayerLimitSettings();
    private final AtomicInteger maxPlayers = new AtomicInteger(20); // server.properties 中的值
    private FeaturesConfig featuresConfig;
    private MockedStatic<Bukkit> bukkit;
    private PlayerLimitFeature feature;

    @BeforeEach
    void setUp() {
        SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
        this.featuresConfig = mock(FeaturesConfig.class, RETURNS_DEEP_STUBS);
        when(plugin.configurationManager().featuresConfig()).thenReturn(this.featuresConfig);
        when(this.featuresConfig.config().playerLimit()).thenReturn(this.settings);
        doAnswer(invocation -> {
            this.settings.maxPlayers(invocation.getArgument(0));
            return null;
        }).when(this.featuresConfig).saveMaxPlayers(anyInt());

        this.bukkit = mockStatic(Bukkit.class);
        this.bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
        this.bukkit.when(Bukkit::getMaxPlayers).thenAnswer(invocation -> this.maxPlayers.get());
        this.bukkit.when(() -> Bukkit.setMaxPlayers(anyInt())).thenAnswer(invocation -> {
            this.maxPlayers.set(invocation.getArgument(0));
            return null;
        });
        this.feature = new PlayerLimitFeature(plugin);
    }

    @AfterEach
    void tearDown() {
        this.bukkit.close();
    }

    @Test
    void keepsServerPropertiesValueByDefault() {
        ((Feature<?>) this.feature).install();
        assertEquals(20, this.feature.maxPlayers());
        assertEquals(20, this.feature.defaultMaxPlayers());
    }

    @Test
    void savedLimitIsAppliedOnEnableAndRestoredOnDisable() {
        this.settings.maxPlayers(50);
        ((Feature<?>) this.feature).install();
        assertEquals(50, this.feature.maxPlayers());
        ((Feature<?>) this.feature).stop();
        assertEquals(20, this.feature.maxPlayers());
        // 重新启用时仍以安装时的值作为默认值
        ((Feature<?>) this.feature).start();
        assertEquals(50, this.feature.maxPlayers());
        assertEquals(20, this.feature.defaultMaxPlayers());
    }

    @Test
    void commandChangesAreSavedAndCanBeReset() {
        ((Feature<?>) this.feature).install();
        this.feature.maxPlayers(0);
        verify(this.featuresConfig).saveMaxPlayers(0);
        assertEquals(0, this.feature.maxPlayers());
        this.feature.maxPlayers(-1);
        verify(this.featuresConfig).saveMaxPlayers(-1);
        assertEquals(20, this.feature.maxPlayers());
    }

    @Test
    void rejectsNegativeLimitOtherThanDefault() throws Exception {
        Field field = PlayerLimitSettings.class.getDeclaredField("maxPlayers");
        field.setAccessible(true);
        field.set(this.settings, -2);
        assertThrows(IllegalArgumentException.class, this.feature::loadConfig);
    }
}
