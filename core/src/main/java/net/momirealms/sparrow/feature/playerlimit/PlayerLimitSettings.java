package net.momirealms.sparrow.feature.playerlimit;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class PlayerLimitSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Maximum number of players. -1 keeps max-players from server.properties. Saved automatically by /max-players.")
    @Comment(lang = "zh", value = "服务器人数上限, -1 表示沿用 server.properties 的 max-players. 使用 /max-players 修改时自动保存.")
    private int maxPlayers = -1;

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int maxPlayers() {
        return this.maxPlayers;
    }

    public void maxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    // 配置错误在启用前报告.
    void validate() {
        if (this.maxPlayers < -1) throw new IllegalArgumentException("player-limit.max-players must be -1 or at least 0");
    }
}
