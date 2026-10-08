package net.momirealms.sparrow.feature.spawn;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.player.teleport.TeleportOptions;
import net.momirealms.sparrow.player.teleport.TeleportSettings;
import net.momirealms.sparrow.player.teleport.TeleportType;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class SpawnSettings implements FeatureSettings {
    private boolean enabled = true;

    @BlankLineBefore
    @Comment("Teleport warmup, cooldown, display and sounds.")
    @Comment(lang = "zh", value = "传送预热、冷却、显示和音效.")
    private TeleportSettings teleport = new TeleportSettings();

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    @NotNull
    public TeleportOptions teleportOptions() {
        return this.teleport.createOptions(TeleportType.SPAWN);
    }
}