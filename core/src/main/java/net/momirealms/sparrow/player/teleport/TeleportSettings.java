package net.momirealms.sparrow.player.teleport;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minecraft.world.BossEvent;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class TeleportSettings {
    @Comment("Seconds to wait before teleporting. sparrow.teleport-warmup.<seconds> overrides it, sparrow.bypass.teleport-warmup skips it.")
    @Comment(lang = "zh", value = "传送前的预热秒数. sparrow.teleport-warmup.<秒> 可覆盖, sparrow.bypass.teleport-warmup 可跳过.")
    private int warmupSeconds = 3;

    @Comment("Cooldown seconds for this teleport type, shared across servers. 0 disables it, sparrow.bypass.teleport-cooldown skips it.")
    @Comment(lang = "zh", value = "此传送类型的冷却秒数, 跨服共享. 0 表示不限制, sparrow.bypass.teleport-cooldown 可跳过.")
    private int cooldownSeconds = 0;

    @Comment("Cancel the warmup when the player moves or takes damage.")
    @Comment(lang = "zh", value = "预热期间移动或受伤时是否取消传送.")
    private boolean cancelOnMove = true;
    private boolean cancelOnDamage = true;

    @BlankLineBefore
    @Comment("Where the warmup countdown is shown: ACTION_BAR, TITLE, BOSS_BAR, CHAT or NONE.")
    @Comment(lang = "zh", value = "预热倒计时显示位置: ACTION_BAR (动作栏)、TITLE (屏幕中央)、BOSS_BAR (进度条)、CHAT (聊天栏) 或 NONE (不显示).")
    private WarmupDisplay warmupDisplay = WarmupDisplay.ACTION_BAR;

    @Comment("Boss bar color: PINK, BLUE, RED, GREEN, YELLOW, PURPLE or WHITE.")
    @Comment(lang = "zh", value = "BossBar 颜色: PINK、BLUE、RED、GREEN、YELLOW、PURPLE 或 WHITE.")
    private BossEvent.BossBarColor bossBarColor = BossEvent.BossBarColor.YELLOW;

    @Comment("Boss bar style: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12 or NOTCHED_20.")
    @Comment(lang = "zh", value = "BossBar 样式: PROGRESS、NOTCHED_6、NOTCHED_10、NOTCHED_12 或 NOTCHED_20.")
    private BossEvent.BossBarOverlay bossBarOverlay = BossEvent.BossBarOverlay.PROGRESS;

    @Comment("Sound keys, such as entity.enderman.teleport. Leave empty to play nothing.")
    @Comment(lang = "zh", value = "音效名称, 例如 entity.enderman.teleport. 留空表示不播放.")
    private String warmupSound = "block.note_block.banjo";
    private String completeSound = "entity.enderman.teleport";
    private String cancelSound = "entity.item.break";

    public void validate() {
        this.warmupSeconds = Math.max(0, this.warmupSeconds);
        this.cooldownSeconds = Math.max(0, this.cooldownSeconds);
    }

    @NotNull
    public TeleportOptions createOptions(@NotNull TeleportType type) {
        this.validate();
        return new TeleportOptions(
                type,
                this.warmupSeconds,
                this.cooldownSeconds,
                this.cancelOnMove,
                this.cancelOnDamage,
                this.warmupDisplay,
                this.bossBarColor,
                this.bossBarOverlay,
                this.parseSound(this.warmupSound),
                this.parseSound(this.completeSound),
                this.parseSound(this.cancelSound)
        );
    }

    @Nullable
    private Sound parseSound(@NotNull String key) {
        if (key.isEmpty() || !Key.parseable(key)) return null;
        return Sound.sound(Key.key(key), Sound.Source.MASTER, 1.0f, 1.0f);
    }
}