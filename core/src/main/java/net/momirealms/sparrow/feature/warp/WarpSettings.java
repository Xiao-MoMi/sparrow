package net.momirealms.sparrow.feature.warp;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minecraft.world.BossEvent;
import net.momirealms.sparrow.service.countdown.CountdownDisplay;
import net.momirealms.sparrow.service.countdown.CountdownSettings;
import net.momirealms.sparrow.util.DurationUtils;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class WarpSettings {
    private static final Sound DEFAULT_WARMUP_SOUND = Sound.sound(Key.key("block.note_block.banjo"), Sound.Source.MASTER, 1.0F, 1.0F);
    private static final Sound DEFAULT_CANCEL_SOUND = Sound.sound(Key.key("entity.item.break"), Sound.Source.MASTER, 1.0F, 1.0F);

    @Comment("Require sparrow.warp.use.<lowercase name> to see and use a warp. Management uses command permissions.")
    @Comment(lang = "zh", value = "查看和使用需要 sparrow.warp.use.<小写名称> 权限, 管理操作使用命令权限.")
    private boolean permissionRestrict = false;
    @Comment("Allowed warp names, up to 32 characters. Names cannot contain whitespace.")
    @Comment(lang = "zh", value = "允许的传送点名称格式, 最长 32 个字符, 不允许空白字符.")
    private String namePattern = "[\\p{L}\\p{N}_][\\p{L}\\p{N}_-]*";
    @BlankLineBefore
    private TeleportOptions teleportOptions = new TeleportOptions();

    WarpConfig toConfig() {
        return new WarpConfig(this.permissionRestrict, Pattern.compile(this.namePattern), this.teleportOptions.toPolicy("teleport-options"));
    }

    private static Duration parseDuration(String path, String text) {
        try {
            if (!text.isEmpty() && text.chars().allMatch(c -> c >= '0' && c <= '9')) return Duration.ofSeconds(Long.parseLong(text));
            return DurationUtils.parseNonNegative(text);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(path + ": invalid duration '" + text + "'", exception);
        }
    }

    private static <E extends Enum<E>> E parseEnum(String path, Class<E> type, String text) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(text)) return constant;
        }
        throw new IllegalArgumentException(path + ": unknown value '" + text + "'");
    }

    @Configuration(naming = Configuration.Naming.SNAKE_CASE)
    public static final class TeleportOptions {
        @Comment("Cooldown starts after success and is shared across servers for this command. 0s bypasses it.")
        @Comment(lang = "zh", value = "成功后开始本命令的跨服冷却. 0s 跳过冷却检查和写入.")
        private String cooldown = "5m";
        private WarmupOptions warmup = WarmupOptions.defaults();
        private String invulnerability = "5s";
        private Sound afterSound = Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.MASTER, 1.0F, 1.0F);
        @Comment("First matching permission wins. Omitted options inherit; a written warmup replaces the whole option.")
        @Comment(lang = "zh", value = "采用第一条匹配权限的覆盖. 未写选项继承基础值, 写出的 warmup 整体替换.")
        private List<OverrideOptions> overrides = List.of(
                OverrideOptions.of("sparrow.warp.teleport.staff", "0s", "0s"),
                OverrideOptions.of("sparrow.warp.teleport.vip", "1m", "3s")
        );

        private WarpConfig.Policy toPolicy(String path) {
            WarpConfig.Options base = new WarpConfig.Options(
                    parseDuration(path + ".cooldown", this.cooldown),
                    this.warmup.toSettings(path + ".warmup"),
                    parseDuration(path + ".invulnerability", this.invulnerability),
                    this.afterSound
            );
            List<WarpConfig.PermissionOverride> overrides = new ArrayList<>(this.overrides.size());
            for (int i = 0, size = this.overrides.size(); i < size; i++) {
                overrides.add(this.overrides.get(i).merge(base, path + ".overrides[" + i + "]"));
            }
            return new WarpConfig.Policy(base, List.copyOf(overrides));
        }
    }

    @Configuration(naming = Configuration.Naming.SNAKE_CASE)
    public static final class WarmupOptions {
        private String duration = "0s";
        @Comment("Cancel when the player moves more than half a block or changes world.")
        @Comment(lang = "zh", value = "玩家移动超过半格或切换世界时取消.")
        private @Nullable Boolean cancelOnMove;
        @Comment("Cancel when the player takes damage.")
        @Comment(lang = "zh", value = "玩家受到伤害时取消.")
        private @Nullable Boolean cancelOnDamage;
        @Comment("action_bar, title, boss_bar, chat or none.")
        @Comment(lang = "zh", value = "倒计时显示位置: action_bar、title、boss_bar、chat 或 none.")
        private @Nullable String display;
        @Comment("Used with boss_bar: pink, blue, red, green, yellow, purple or white.")
        @Comment(lang = "zh", value = "display 为 boss_bar 时使用: pink、blue、red、green、yellow、purple 或 white.")
        private @Nullable String bossBarColor;
        @Comment("Used with boss_bar: progress, notched_6, notched_10, notched_12 or notched_20.")
        @Comment(lang = "zh", value = "display 为 boss_bar 时使用: progress、notched_6、notched_10、notched_12 或 notched_20.")
        private @Nullable String bossBarOverlay;
        @Comment("Played every second of the countdown.")
        @Comment(lang = "zh", value = "倒计时每秒播放.")
        private @Nullable Sound warmupSound;
        @Comment("Played when moving or taking damage cancels the countdown.")
        @Comment(lang = "zh", value = "因移动或受伤取消时播放.")
        private @Nullable Sound cancelSound;

        // 只写时长时其余选项留空, 转换时取默认值
        @NotNull
        @ApiStatus.Internal
        public static WarmupOptions of(@NotNull String duration) {
            WarmupOptions options = new WarmupOptions();
            options.duration = duration;
            return options;
        }

        // 生成默认文件时写出全部选项
        static WarmupOptions defaults() {
            WarmupOptions options = of("5s");
            options.cancelOnMove = true;
            options.cancelOnDamage = true;
            options.display = "action_bar";
            options.bossBarColor = "yellow";
            options.bossBarOverlay = "progress";
            options.warmupSound = DEFAULT_WARMUP_SOUND;
            options.cancelSound = DEFAULT_CANCEL_SOUND;
            return options;
        }

        private CountdownSettings toSettings(String path) {
            return new CountdownSettings(
                    parseDuration(path + ".duration", this.duration),
                    this.cancelOnMove == null || this.cancelOnMove,
                    this.cancelOnDamage == null || this.cancelOnDamage,
                    this.display == null ? CountdownDisplay.ACTION_BAR : parseEnum(path + ".display", CountdownDisplay.class, this.display),
                    this.bossBarColor == null ? BossEvent.BossBarColor.YELLOW : parseEnum(path + ".boss_bar_color", BossEvent.BossBarColor.class, this.bossBarColor),
                    this.bossBarOverlay == null ? BossEvent.BossBarOverlay.PROGRESS : parseEnum(path + ".boss_bar_overlay", BossEvent.BossBarOverlay.class, this.bossBarOverlay),
                    this.warmupSound == null ? DEFAULT_WARMUP_SOUND : this.warmupSound,
                    this.cancelSound == null ? DEFAULT_CANCEL_SOUND : this.cancelSound
            );
        }
    }

    // 覆盖条目中没写的选项为 null, 沿用原始设置
    @Configuration(naming = Configuration.Naming.SNAKE_CASE)
    public static final class OverrideOptions {
        private @Nullable String permission;
        private @Nullable String cooldown;
        private @Nullable WarmupOptions warmup;
        private @Nullable String invulnerability;
        private @Nullable Sound afterSound;

        static OverrideOptions of(String permission, String cooldown, String warmup) {
            OverrideOptions options = new OverrideOptions();
            options.permission = permission;
            options.cooldown = cooldown;
            options.warmup = WarmupOptions.of(warmup);
            return options;
        }

        private WarpConfig.PermissionOverride merge(WarpConfig.Options base, String path) {
            if (this.permission == null || this.permission.isEmpty()) throw new IllegalArgumentException(path + ": permission is required");
            return new WarpConfig.PermissionOverride(this.permission, new WarpConfig.Options(
                    this.cooldown == null ? base.cooldown() : parseDuration(path + ".cooldown", this.cooldown),
                    this.warmup == null ? base.warmup() : this.warmup.toSettings(path + ".warmup"),
                    this.invulnerability == null ? base.invulnerability() : parseDuration(path + ".invulnerability", this.invulnerability),
                    this.afterSound == null ? base.afterSound() : this.afterSound
            ));
        }
    }
}