package net.momirealms.sparrow.feature.spawn;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.player.teleport.TeleportOptions;
import net.momirealms.sparrow.player.teleport.TeleportType;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class SpawnSettings implements FeatureSettings {
    private boolean enabled = true;

    @BlankLineBefore
    @Comment("Seconds a player must stand still before returning to spawn. The usual teleport warmup permissions apply.")
    @Comment(lang = "zh", value = "返回 Spawn 前需要原地等待的秒数. 支持现有传送预热权限.")
    private int warmupSeconds = 3;

    @Comment("Seconds before a player can use /spawn again, shared across servers. 0 disables it. The cooldown bypass permission applies.")
    @Comment(lang = "zh", value = "两次 /spawn 之间的冷却秒数, 各服务器共享. 0 表示不限制. 支持现有冷却绕过权限.")
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

    @NotNull
    public TeleportOptions teleportOptions() {
        return new TeleportOptions(TeleportType.SPAWN, this.warmupSeconds, this.cooldownSeconds, this.cancelOnMove, this.cancelOnDamage);
    }
}
