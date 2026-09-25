package net.momirealms.sparrow.feature;

import net.momirealms.sparrow.feature.server.ServerFeature;
import net.momirealms.sparrow.feature.server.ServerSettings;
import net.momirealms.sparrow.plugin.configuration.FeaturesConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerFeatureTest {

    @Test
    void emptyAllowListAllowsEveryServer() throws Exception {
        ServerSettings settings = new ServerSettings();
        FeaturesConfig config = mock(FeaturesConfig.class, RETURNS_DEEP_STUBS);
        when(config.config().server()).thenReturn(settings);
        ServerFeature feature = new ServerFeature(config);
        ((Feature<?>) feature).install();
        assertTrue(feature.allowed("anything"));
        Field field = ServerSettings.class.getDeclaredField("allowedServers");
        field.setAccessible(true);
        field.set(settings, List.of("survival"));
        assertTrue(feature.allowed("survival"));
        assertFalse(feature.allowed("minigame"));
    }
}
