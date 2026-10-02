package net.momirealms.sparrow.feature.bed;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.player.teleport.TeleportOptions;
import net.momirealms.sparrow.player.teleport.TeleportType;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class BedSettings implements FeatureSettings {
    private boolean enabled = true;

    @BlankLineBefore
    @Comment("Seconds a player must stand still before returning to their bed. sparrow.teleport-warmup.<seconds> overrides it (lowest node wins), sparrow.bypass.teleport-warmup skips it.")
    @Comment(lang = "zh", value = "回到床边前需要原地等待的秒数. sparrow.teleport-warmup.<秒> 可覆盖该值 (取最小的节点), sparrow.bypass.teleport-warmup 可跳过.")
    private int warmupSeconds = 3;

    @Comment("Seconds before a player can use /bed again, shared across servers. 0 disables it. sparrow.bypass.teleport-cooldown skips it.")
    @Comment(lang = "zh", value = "两次 /bed 之间的冷却秒数, 各服务器共享. 0 表示不限制. sparrow.bypass.teleport-cooldown 可跳过.")
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

    /** 生成 /bed 的默认预热与冷却参数. */
    @NotNull
    public TeleportOptions teleportOptions() {
        return new TeleportOptions(TeleportType.BED, this.warmupSeconds, this.cooldownSeconds, this.cancelOnMove, this.cancelOnDamage);
    }
}
