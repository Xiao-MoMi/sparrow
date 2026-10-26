package net.momirealms.sparrow.feature.rtp;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minecraft.world.BossEvent;
import net.momirealms.sparrow.service.countdown.CountdownDisplay;
import net.momirealms.sparrow.service.countdown.CountdownSettings;
import net.momirealms.sparrow.teleport.position.rtp.RtpArea;
import net.momirealms.sparrow.util.DurationUtils;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class RtpSettings {
    private static final Sound DEFAULT_WARMUP_SOUND = Sound.sound(Key.key("block.note_block.banjo"), Sound.Source.MASTER, 1.0F, 1.0F);
    private static final Sound DEFAULT_CANCEL_SOUND = Sound.sound(Key.key("entity.item.break"), Sound.Source.MASTER, 1.0F, 1.0F);

    @Comment({
            "Teleport options for /rtp and /rtp_range. Durations accept d/h/m/s/ms, a plain number means seconds, and 0s turns the option off.",
            "cooldown: wait before the same player can RTP again. It starts after a successful teleport and is shared across servers.",
            "warmup: countdown before departure while the target server searches for a spot. A single duration such as 3s keeps the other options at their defaults.",
            "invulnerability: vanilla invulnerability after arrival.",
            "after_sound: sound played on arrival, a key or a section with key, volume, pitch and source. '' plays nothing.",
            "overrides: checked from top to bottom. The first entry whose permission the player has replaces the options it writes; options it leaves out keep the values above.",
            "   A written option is replaced as a whole, its unwritten parts use built-in defaults. OPs have these permissions by default, so put the highest tier first."
    })
    @Comment(lang = "zh", value = {
            "/rtp 与 /rtp_range 的传送选项. 时长支持 d/h/m/s/ms, 只写数字表示秒, 0s 表示关闭该项.",
            "cooldown: 同一玩家再次随机传送前的冷却. 传送成功后才开始计时, 跨服共享.",
            "warmup: 出发前的倒计时, 期间目标服已经在搜索落点. 只写一个时长 (如 3s) 时, 其余选项使用默认值.",
            "invulnerability: 到达后的原版无敌时长.",
            "after_sound: 到达后播放的音效, 可以只写名字, 也可以写成包含 key、volume、pitch、source 的小节. 写 '' 表示不播放.",
            "overrides: 从上到下检查, 采用玩家拥有权限的第一条, 用它写出的选项替换上面的值, 没写的选项保持不变.",
            "   写出的选项整体替换, 选项内没写的部分使用内置默认值. OP 默认拥有这些权限, 所以权限最高的一档写在最前面."
    })
    private TeleportOptions teleportOptions = TeleportOptions.defaults();

    @BlankLineBefore
    @Comment({
            "Regions for /rtp <region>. The key is the region name.",
            "server: target server ID. Leave it out to use the server the player is on.",
            "permission: required to travel to the region. Leave it out to allow everyone who can use /rtp.",
            "area: world name -> rectangles. x and z are inclusive block ranges, y is an optional feet height range.",
            "All worlds of one region must be on the same server."
    })
    @Comment(lang = "zh", value = {
            "/rtp <区域> 使用的区域, 键是区域名.",
            "server: 目标服务器 ID. 不写表示玩家当前所在的服务器.",
            "permission: 前往该区域需要的权限. 不写表示能使用 /rtp 的玩家都可以去.",
            "area: 世界名 -> 矩形列表. x、z 是包含两端的方块范围, y 是可选的脚底高度范围.",
            "同一区域内的所有世界必须在同一台服务器上."
    })
    private Map<String, RegionOptions> regions = Map.of("wild", RegionOptions.defaults());

    // 校验并转换成运行时使用的不可变配置
    @NotNull
    RtpConfig toConfig() {
        RtpConfig.Options base = this.teleportOptions.toOptions();
        List<OverrideOptions> entries = this.teleportOptions.overrides;
        List<RtpConfig.PermissionOverride> overrides = new ArrayList<>(entries.size());
        for (int i = 0, size = entries.size(); i < size; i++) {
            overrides.add(entries.get(i).merge(base, "teleport-options.overrides[" + i + "]"));
        }
        Map<String, RtpConfig.Region> regions = new LinkedHashMap<>();
        this.regions.forEach((name, region) -> regions.put(name, region.toRegion(name)));
        return new RtpConfig(base, List.copyOf(overrides), regions);
    }

    // 纯数字按秒, 其余按 DurationUtils 的写法, 允许 0
    private static Duration parseDuration(String path, String text) {
        if (!text.isEmpty() && text.chars().allMatch(c -> c >= '0' && c <= '9')) return Duration.ofSeconds(Long.parseLong(text));
        try {
            return DurationUtils.parseNonNegative(text);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(path + ": invalid duration '" + text + "'", exception);
        }
    }

    // 取值写成小写, 读取时不区分大小写
    private static <E extends Enum<E>> E parseEnum(String path, Class<E> type, String text) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(text)) return constant;
        }
        throw new IllegalArgumentException(path + ": unknown value '" + text + "'");
    }

    @Configuration(naming = Configuration.Naming.SNAKE_CASE)
    public static final class TeleportOptions {
        private String cooldown = "5m";
        private WarmupOptions warmup = WarmupOptions.defaults();
        private String invulnerability = "5s";
        private Sound afterSound = Sound.sound(Key.key("entity.enderman.teleport"), Sound.Source.MASTER, 1.0F, 1.0F);
        private List<OverrideOptions> overrides = List.of(
                OverrideOptions.of("sparrow.rtp.staff", "0s", "0s"),
                OverrideOptions.of("sparrow.rtp.vip", "1m", "3s")
        );

        static TeleportOptions defaults() {
            return new TeleportOptions();
        }

        private RtpConfig.Options toOptions() {
            return new RtpConfig.Options(
                    parseDuration("teleport-options.cooldown", this.cooldown),
                    this.warmup.toSettings("teleport-options.warmup"),
                    parseDuration("teleport-options.invulnerability", this.invulnerability),
                    this.afterSound
            );
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

        private RtpConfig.PermissionOverride merge(RtpConfig.Options base, String path) {
            if (this.permission == null || this.permission.isEmpty()) throw new IllegalArgumentException(path + ": permission is required");
            return new RtpConfig.PermissionOverride(this.permission, new RtpConfig.Options(
                    this.cooldown == null ? base.cooldown() : parseDuration(path + ".cooldown", this.cooldown),
                    this.warmup == null ? base.warmup() : this.warmup.toSettings(path + ".warmup"),
                    this.invulnerability == null ? base.invulnerability() : parseDuration(path + ".invulnerability", this.invulnerability),
                    this.afterSound == null ? base.afterSound() : this.afterSound
            ));
        }
    }

    @Configuration(naming = Configuration.Naming.SNAKE_CASE)
    public static final class RegionOptions {
        private @Nullable String server;
        private @Nullable String permission;
        private Map<String, List<RectangleOptions>> area = Map.of();

        static RegionOptions defaults() {
            RegionOptions options = new RegionOptions();
            options.area = Map.of("world", List.of(RectangleOptions.of(-5000, 5000, -5000, 5000)));
            return options;
        }

        private RtpConfig.Region toRegion(String name) {
            String path = "regions." + name;
            if (this.area.isEmpty()) throw new IllegalArgumentException(path + ".area: at least one world is required");
            List<RtpArea> areas = new ArrayList<>(this.area.size());
            this.area.forEach((world, rectangles) -> {
                if (rectangles.isEmpty()) throw new IllegalArgumentException(path + ".area." + world + ": at least one rectangle is required");
                RtpArea.Builder builder = RtpArea.builder(world);
                for (int i = 0, size = rectangles.size(); i < size; i++) {
                    rectangles.get(i).addTo(builder, path + ".area." + world + "[" + i + "]");
                }
                areas.add(builder.build());
            });
            return new RtpConfig.Region(name, this.server, this.permission, List.copyOf(areas));
        }
    }

    @Configuration(naming = Configuration.Naming.SNAKE_CASE)
    public static final class RectangleOptions {
        private List<Integer> x = List.of();
        private List<Integer> z = List.of();
        private @Nullable List<Double> y;

        static RectangleOptions of(int minX, int maxX, int minZ, int maxZ) {
            RectangleOptions options = new RectangleOptions();
            options.x = List.of(minX, maxX);
            options.z = List.of(minZ, maxZ);
            return options;
        }

        private void addTo(RtpArea.Builder builder, String path) {
            if (this.x.size() != 2 || this.z.size() != 2 || (this.y != null && this.y.size() != 2)) {
                throw new IllegalArgumentException(path + ": x, z and y must each contain a minimum and a maximum");
            }
            try {
                if (this.y == null) {
                    builder.rectangle(this.x.get(0), this.z.get(0), this.x.get(1), this.z.get(1));
                } else {
                    builder.rectangle(this.x.get(0), this.z.get(0), this.x.get(1), this.z.get(1), this.y.get(0), this.y.get(1));
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(path + ": " + exception.getMessage(), exception);
            }
        }
    }
}