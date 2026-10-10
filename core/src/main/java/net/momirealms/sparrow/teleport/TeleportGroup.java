package net.momirealms.sparrow.teleport;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minecraft.world.BossEvent;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class TeleportGroup {
    @Comment({
            "Processors to run, in order. Built-in processors:",
            "   cooldown - rejects the teleport while the cooldown is running, and starts the cooldown after a successful teleport.",
            "   warmup - makes the player wait in place before leaving, with a countdown and sounds.",
            "   world-blacklist - rejects teleports into the listed worlds."
    })
    @Comment(lang = "zh", value = {
            "按顺序执行的处理器. 内置处理器:",
            "   cooldown - 冷却期间拒绝传送, 传送成功后开始冷却.",
            "   warmup - 出发前让玩家原地等待, 带倒计时和音效.",
            "   world-blacklist - 拒绝传送进入列出的世界."
    })
    private List<String> processors = List.of("cooldown", "warmup", "world-blacklist");

    @BlankLineBefore
    @Comment("Options of the cooldown processor.")
    @Comment(lang = "zh", value = "cooldown 处理器的选项.")
    private Cooldown cooldown = new Cooldown();

    @BlankLineBefore
    @Comment("Options of the warmup processor.")
    @Comment(lang = "zh", value = "warmup 处理器的选项.")
    private Warmup warmup = new Warmup();

    @BlankLineBefore
    @Comment("Options of the world-blacklist processor.")
    @Comment(lang = "zh", value = "world-blacklist 处理器的选项.")
    private WorldBlacklist worldBlacklist = new WorldBlacklist();

    // 把分组选项整理成传送服务使用的参数快照.
    @NotNull
    public TeleportOptions createOptions(@NotNull TeleportType type) {
        return new TeleportOptions(
                this.cooldown.id.isEmpty() ? type.id() : this.cooldown.id,
                Math.max(0, this.warmup.seconds),
                Math.max(0, this.cooldown.seconds),
                this.warmup.cancelOnMove,
                this.warmup.cancelOnDamage,
                this.warmup.display,
                this.warmup.bossBarColor,
                this.warmup.bossBarOverlay,
                parseSound(this.warmup.warmupSound),
                parseSound(this.warmup.completeSound),
                parseSound(this.warmup.cancelSound)
        );
    }

    @Nullable
    private static Sound parseSound(@NotNull String key) {
        if (key.isEmpty() || !Key.parseable(key)) return null;
        return Sound.sound(Key.key(key), Sound.Source.MASTER, 1.0f, 1.0f);
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class Cooldown {
        @Comment({
                "Teleports with the same cooldown ID share one cooldown.",
                "Leave empty to use the kind of teleport as the ID, so that home, warp, back and the others each have their own cooldown."
        })
        @Comment(lang = "zh", value = {
                "冷却 ID 相同的传送共用一份冷却.",
                "留空时以传送的种类作为 ID, home、warp、back 等各自独立冷却."
        })
        private String id = "";

        @Comment("Cooldown seconds, shared across servers. 0 disables it, sparrow.bypass.teleport-cooldown skips it.")
        @Comment(lang = "zh", value = "冷却秒数, 跨服共享. 0 表示不限制, sparrow.bypass.teleport-cooldown 可跳过.")
        private int seconds = 0;
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class Warmup {
        @Comment("Seconds to wait before teleporting. sparrow.teleport-warmup.<seconds> overrides it, sparrow.bypass.teleport-warmup skips it.")
        @Comment(lang = "zh", value = "传送前的预热秒数. sparrow.teleport-warmup.<秒> 可覆盖, sparrow.bypass.teleport-warmup 可跳过.")
        private int seconds = 3;

        @Comment("Cancel the warmup when the player moves or takes damage.")
        @Comment(lang = "zh", value = "预热期间移动或受伤时是否取消传送.")
        private boolean cancelOnMove = true;
        private boolean cancelOnDamage = true;

        @BlankLineBefore
        @Comment("Where the warmup countdown is shown: ACTION_BAR, TITLE, BOSS_BAR, CHAT or NONE.")
        @Comment(lang = "zh", value = "预热倒计时显示位置: ACTION_BAR (动作栏)、TITLE (屏幕中央)、BOSS_BAR (进度条)、CHAT (聊天栏) 或 NONE (不显示).")
        private WarmupDisplay display = WarmupDisplay.ACTION_BAR;

        @Comment("Boss bar color: PINK, BLUE, RED, GREEN, YELLOW, PURPLE or WHITE.")
        @Comment(lang = "zh", value = "BossBar 颜色: PINK、BLUE、RED、GREEN、YELLOW、PURPLE 或 WHITE.")
        private BossEvent.BossBarColor bossBarColor = BossEvent.BossBarColor.YELLOW;

        @Comment("Boss bar style: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12 or NOTCHED_20.")
        @Comment(lang = "zh", value = "BossBar 样式: PROGRESS、NOTCHED_6、NOTCHED_10、NOTCHED_12 或 NOTCHED_20.")
        private BossEvent.BossBarOverlay bossBarOverlay = BossEvent.BossBarOverlay.PROGRESS;

        @Comment({
                "Sound keys, such as entity.enderman.teleport. Leave empty to play nothing.",
                "warmup-sound plays every second of the countdown, cancel-sound when the warmup is cancelled, complete-sound on arrival."
        })
        @Comment(lang = "zh", value = {
                "音效名称, 例如 entity.enderman.teleport. 留空表示不播放.",
                "warmup-sound 在倒计时每秒播放, cancel-sound 在预热被取消时播放, complete-sound 在到达时播放."
        })
        private String warmupSound = "block.note_block.banjo";
        private String cancelSound = "entity.item.break";
        private String completeSound = "entity.enderman.teleport";
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class WorldBlacklist {
        @Comment("Worlds that players cannot teleport into. The server the world belongs to runs the check.")
        @Comment(lang = "zh", value = "禁止传送进入的世界, 由世界所在的服务器检查.")
        private List<String> worlds = List.of();
    }
}
