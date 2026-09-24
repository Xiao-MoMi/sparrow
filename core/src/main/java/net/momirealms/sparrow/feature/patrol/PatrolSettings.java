package net.momirealms.sparrow.feature.patrol;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class PatrolSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Skip players in spectator mode when choosing the next player to patrol.")
    @Comment(lang = "zh", value = "选择巡查对象时是否跳过旁观模式的玩家.")
    private boolean skipSpectators = true;

    @Comment("Players in these worlds are never chosen for patrol. An empty list allows all worlds.")
    @Comment(lang = "zh", value = "位于这些世界的玩家不会被选为巡查对象, 空列表表示不限制世界.")
    private List<String> excludedWorlds = List.of();

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean skipSpectators() {
        return this.skipSpectators;
    }

    @NotNull
    public List<String> excludedWorlds() {
        return this.excludedWorlds;
    }
}
