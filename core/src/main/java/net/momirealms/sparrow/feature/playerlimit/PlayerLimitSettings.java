package net.momirealms.sparrow.feature.playerlimit;

import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class PlayerLimitSettings {
    @Comment("Maximum players. -1 restores the limit from before this module was enabled. /max_players saves changes here. The effective limit is also written to server.properties.")
    @Comment(lang = "zh", value = "人数上限, -1 表示恢复模块启用前的上限. /max_players 修改后保存在此处. 生效的人数上限也会写入 server.properties.")
    private int maxPlayers = -1;

    public int maxPlayers() {
        return this.maxPlayers;
    }

    public void maxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}