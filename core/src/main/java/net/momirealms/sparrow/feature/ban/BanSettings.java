package net.momirealms.sparrow.feature.ban;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class BanSettings implements FeatureSettings {
    private boolean enabled = false; // 开启后才会创建封禁表, 并在每次登录时查询封禁

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }
}
