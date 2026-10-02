package net.momirealms.sparrow.feature;

import net.momirealms.sparrow.feature.bed.BedCommand;
import net.momirealms.sparrow.feature.bed.BedFeature;
import net.momirealms.sparrow.feature.bed.BedSettings;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.command.FeaturePermission;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BedFeatureTest {
    @Test
    void registersItsCommandAndFollowsTheFeatureLifecycle() {
        SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
        BedSettings settings = new BedSettings();
        when(plugin.configurationManager().featuresConfig().config().bed()).thenReturn(settings);
        BedFeature bed = new BedFeature(plugin);
        Feature<?> feature = bed;
        List<CommandFeature> commands = new ArrayList<>();
        feature.registerCommand(commands::add);
        BedCommand command = assertInstanceOf(BedCommand.class, commands.getFirst());
        assertEquals(BedFeature.ID, command.getFeatureID());
        FeaturePermission requirement = new FeaturePermission(BedFeature.ID, bed::enabled);
        assertSame(requirement, FeaturePermission.findDisabled(requirement));

        feature.install();
        assertSame(settings, bed.config());
        assertNull(FeaturePermission.findDisabled(requirement));
        feature.stop();
        assertSame(requirement, FeaturePermission.findDisabled(requirement));

        BedSettings reloaded = new BedSettings();
        when(plugin.configurationManager().featuresConfig().config().bed()).thenReturn(reloaded);
        bed.loadConfig();
        feature.start();
        assertSame(reloaded, bed.config());
        assertNull(FeaturePermission.findDisabled(requirement));
        assertEquals(1, commands.size());
    }
}
