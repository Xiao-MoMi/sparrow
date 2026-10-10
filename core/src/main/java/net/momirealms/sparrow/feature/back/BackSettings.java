package net.momirealms.sparrow.feature.back;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class BackSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Save the last death location and server to the database for /death-back.")
    @Comment(lang = "zh", value = "把最后死亡位置和服务器保存到数据库, 供 /death-back 返回.")
    private boolean recordDeath = true;

    @Comment({
            "Teleports with these causes record the location before teleporting. Common values:",
            "COMMAND, PLUGIN, NETHER_PORTAL, END_PORTAL, END_GATEWAY, ENDER_PEARL, SPECTATE"
    })
    @Comment(lang = "zh", value = {
            "这些原因的传送会记录传送前的位置. 常用值:",
            "COMMAND (命令), PLUGIN (插件), NETHER_PORTAL (下界传送门), END_PORTAL (末地传送门), END_GATEWAY (末地折跃门), ENDER_PEARL (末影珍珠), SPECTATE (旁观传送)"
    })
    private List<String> teleportCauses = List.of("COMMAND", "PLUGIN");

    @Comment("Teleports within this many seconds after joining the server are not recorded. Other plugins often move players to spawn right after they join.")
    @Comment(lang = "zh", value = "进入服务器后多少秒内的传送不记录. 其他插件经常在玩家进服时把人传到出生点, 这段时间的传送不算作返回位置.")
    private int joinGraceSeconds = 3;

    @Comment("When nothing is recorded on this server, /back returns to the previous server if the player came from it within this many seconds.")
    @Comment(lang = "zh", value = "本服没有记录时, 如果玩家是在这么多秒内从上一个服务器切换过来的, /back 会回到上一个服务器离开时的位置.")
    private int serverSwitchWindowSeconds = 30;

    @Comment("Teleport group from teleport.yml used by /back and /death-back. Leave empty to use the default group.")
    @Comment(lang = "zh", value = "/back 与 /death-back 使用的传送分组, 在 teleport.yml 中定义. 留空时使用 default 分组.")
    private String teleportGroup = "default";

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean recordDeath() {
        return this.recordDeath;
    }

    @NotNull
    public List<String> teleportCauses() {
        return this.teleportCauses;
    }

    public int joinGraceSeconds() {
        return this.joinGraceSeconds;
    }

    public int serverSwitchWindowSeconds() {
        return this.serverSwitchWindowSeconds;
    }

    @NotNull
    public String teleportGroup() {
        return this.teleportGroup;
    }
}