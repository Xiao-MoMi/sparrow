package net.momirealms.sparrow.feature.server;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class ServerSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Backend servers players may be sent to. An empty list allows every server.")
    @Comment(lang = "zh", value = "允许切换到的后端服务器, 空列表表示不限制.")
    private List<String> allowedServers = List.of();

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    @NotNull
    public List<String> allowedServers() {
        return this.allowedServers;
    }
}
