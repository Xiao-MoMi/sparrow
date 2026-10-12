package net.momirealms.sparrow.feature.rtp;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class RtpSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Teleport group from teleport.yml used by /rtp. The destination server must use the random search strategy.")
    @Comment(lang = "zh", value = "/rtp 使用的传送分组, 在 teleport.yml 中定义. 目标服的分组必须使用 random 搜索策略.")
    private String teleportGroup = "rtp";

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