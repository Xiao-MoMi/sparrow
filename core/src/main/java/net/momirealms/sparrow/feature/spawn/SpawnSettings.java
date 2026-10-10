package net.momirealms.sparrow.feature.spawn;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class SpawnSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Teleport group from teleport.yml used by /spawn. Leave empty to use the default group.")
    @Comment(lang = "zh", value = "/spawn 使用的传送分组, 在 teleport.yml 中定义. 留空时使用 default 分组.")
    private String teleportGroup = "default";

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    @NotNull
    public String teleportGroup() {
        return this.teleportGroup;
    }
}