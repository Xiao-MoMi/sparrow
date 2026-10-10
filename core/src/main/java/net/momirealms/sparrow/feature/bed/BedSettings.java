package net.momirealms.sparrow.feature.bed;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.plugin.configuration.TeleportConfig;
import net.momirealms.sparrow.teleport.TeleportOptions;
import net.momirealms.sparrow.teleport.TeleportType;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class BedSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Teleport group from teleport.yml used by /bed. Leave empty to use the default group.")
    @Comment(lang = "zh", value = "/bed 使用的传送分组, 在 teleport.yml 中定义. 留空时使用 default 分组.")
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

    /** 生成 /bed 的默认预热与冷却参数. */
    @NotNull
    public TeleportOptions teleportOptions() {
        return TeleportConfig.group(this.teleportGroup).createOptions(TeleportType.BED);
    }
}