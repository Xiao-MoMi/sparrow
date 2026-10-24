package net.momirealms.sparrow.feature.patrol;

import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class PatrolSettings {
    @Comment("Skip players in spectator mode when choosing the next player to patrol.")
    @Comment(lang = "zh", value = "选择巡查对象时是否跳过旁观模式的玩家.")
    private boolean skipSpectators = true;

    public boolean skipSpectators() {
        return this.skipSpectators;
    }
}