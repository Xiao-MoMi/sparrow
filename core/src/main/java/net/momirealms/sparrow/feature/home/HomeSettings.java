package net.momirealms.sparrow.feature.home;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class HomeSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Seconds before online home data is refreshed from the database on its next use. Must be positive.")
    @Comment(lang = "zh", value = "在线玩家的 Home 缓存有效秒数, 过期后在下次使用时读库刷新. 必须大于 0.")
    private int cacheTtlSeconds = 300;

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int cacheTtlSeconds() {
        return this.cacheTtlSeconds;
    }
}