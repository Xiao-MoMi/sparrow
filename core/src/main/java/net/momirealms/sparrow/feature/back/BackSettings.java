package net.momirealms.sparrow.feature.back;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.player.teleport.TeleportOptions;
import net.momirealms.sparrow.player.teleport.TeleportType;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class BackSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Record where players die, so /back can return them to the death location.")
    @Comment(lang = "zh", value = "是否记录死亡位置, 开启后 /back 可以回到死亡的地方.")
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

    @BlankLineBefore
    @Comment("Seconds a player must stand still before returning. sparrow.teleport-warmup.<seconds> overrides it (lowest node wins), sparrow.bypass.teleport-warmup skips it.")
    @Comment(lang = "zh", value = "返回前需要原地等待的秒数. sparrow.teleport-warmup.<秒> 可覆盖该值 (取最小的节点), sparrow.bypass.teleport-warmup 可跳过.")
    private int warmupSeconds = 3;

    @Comment("Seconds before a player can use /back again, shared across servers. 0 disables it. sparrow.bypass.teleport-cooldown skips it.")
    @Comment(lang = "zh", value = "两次 /back 之间的冷却秒数, 各服务器共享. 0 表示不限制. sparrow.bypass.teleport-cooldown 可跳过.")
    private int cooldownSeconds = 0;

    @Comment("Cancel the warmup when the player moves or takes damage.")
    @Comment(lang = "zh", value = "预热期间移动或受伤时是否取消传送.")
    private boolean cancelOnMove = true;
    private boolean cancelOnDamage = true;

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

    /** 生成 /back 的默认预热与冷却参数. */
    @NotNull
    public TeleportOptions teleportOptions() {
        return new TeleportOptions(TeleportType.BACK, this.warmupSeconds, this.cooldownSeconds, this.cancelOnMove, this.cancelOnDamage);
    }
}
