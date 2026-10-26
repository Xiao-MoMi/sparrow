package net.momirealms.sparrow.feature.spawn;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minecraft.world.BossEvent;
import net.momirealms.sparrow.service.countdown.CountdownDisplay;
import net.momirealms.sparrow.service.countdown.CountdownSettings;
import net.momirealms.sparrow.util.DurationUtils;
import net.momirealms.sparrow.util.ServerLocation;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class SpawnSettings {
    private Destination destination = new Destination();
    @BlankLineBefore
    private TeleportOptions teleportOptions = new TeleportOptions();

    SpawnConfig toConfig() {
        SpawnConfig.Options base = new SpawnConfig.Options(
                parseDuration("teleport-options.cooldown", this.teleportOptions.cooldown),
                this.teleportOptions.warmup.toSettings("teleport-options.warmup")
        );
        List<OverrideOptions> configured = this.teleportOptions.overrides;
        List<SpawnConfig.PermissionOverride> overrides = new ArrayList<>(configured.size());
        for (int i = 0, size = configured.size(); i < size; i++) {
            overrides.add(configured.get(i).merge(base, "teleport-options.overrides[" + i + "]"));
        }
        return new SpawnConfig(this.destination.toLocation(), base, List.copyOf(overrides));
    }

    private static Duration parseDuration(String path, String text) {
        try {
            if (!text.isEmpty() && text.chars().allMatch(c -> c >= '0' && c <= '9')) return Duration.ofSeconds(Long.parseLong(text));
            return DurationUtils.parseNonNegative(text);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(path + ": invalid duration '" + text + "'", exception);
        }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class Destination {
        @Comment("Destination server ID from server.yml. Leave server and world empty until configured.")
        @Comment(lang = "zh", value = "目标服 server.yml 中的服务器 ID. 尚未配置时将 server 和 world 留空.")
        private String server = "";
        private String world = "";
        @Comment("Exact destination coordinates. No safe-location search is performed.")
        @Comment(lang = "zh", value = "直接传送到指定坐标, 不搜索安全位置.")
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;

        @Nullable
        private ServerLocation toLocation() {
            if (this.server.isBlank() && this.world.isBlank()) return null;
            if (this.server.isBlank() || this.world.isBlank()) throw new IllegalArgumentException("destination.server and destination.world are required together");
            if (!Double.isFinite(this.x) || !Double.isFinite(this.y) || !Double.isFinite(this.z) || !Float.isFinite(this.yaw) || !Float.isFinite(this.pitch)) {
                throw new IllegalArgumentException("destination coordinates and rotation must be finite");
            }
            return new ServerLocation(this.server, this.world, this.x, this.y, this.z, this.yaw, this.pitch);
        }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class TeleportOptions {
        @Comment("Shared across servers for /spawn. Starts after success; 0s skips cooldown.")
        @Comment(lang = "zh", value = "/spawn 的跨服冷却, 传送成功后开始. 0s 跳过冷却.")
        private String cooldown = "5m";
        private WarmupOptions warmup = WarmupOptions.defaults();
        @Comment("First matching permission wins. Omitted options inherit; a written warmup replaces the whole option.")
        @Comment(lang = "zh", value = "采用第一条匹配权限的覆盖. 未写选项继承基础值, 写出的 warmup 整体替换.")
        private List<OverrideOptions> overrides = List.of(OverrideOptions.staff());
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static class WarmupOptions {
        private String duration = "0s";
        @Comment("Cancel when the player moves more than half a block or changes world.")
        @Comment(lang = "zh", value = "玩家移动超过半格或切换世界时取消.")
        private boolean cancelOnMove = true;
        private boolean cancelOnDamage = true;
        @Comment("action_bar, title, boss_bar, chat or none.")
        @Comment(lang = "zh", value = "倒计时显示位置: action_bar、title、boss_bar、chat 或 none.")
        private String display = "action_bar";
        private String bossBarColor = "yellow";
        private String bossBarOverlay = "progress";
        private Sound warmupSound = Sound.sound(Key.key("block.note_block.banjo"), Sound.Source.MASTER, 1.0F, 1.0F);
        private Sound cancelSound = Sound.sound(Key.key("entity.item.break"), Sound.Source.MASTER, 1.0F, 1.0F);

        private static WarmupOptions defaults() {
            WarmupOptions options = new WarmupOptions();
            options.duration = "5s";
            return options;
        }

        CountdownSettings toSettings(String path) {
            return new CountdownSettings(
                    parseDuration(path + ".duration", this.duration),
                    this.cancelOnMove,
                    this.cancelOnDamage,
                    CountdownDisplay.valueOf(this.display.toUpperCase(Locale.ROOT)),
                    BossEvent.BossBarColor.valueOf(this.bossBarColor.toUpperCase(Locale.ROOT)),
                    BossEvent.BossBarOverlay.valueOf(this.bossBarOverlay.toUpperCase(Locale.ROOT)),
                    this.warmupSound,
                    this.cancelSound
            );
        }
    }

    @Configuration(naming = Configuration.Naming.SNAKE_CASE)
    public static final class OverrideWarmupOptions extends WarmupOptions {
    }

    @Configuration(naming = Configuration.Naming.SNAKE_CASE)
    public static final class OverrideOptions {
        private String permission = "";
        private @Nullable String cooldown;
        private @Nullable OverrideWarmupOptions warmup;

        private static OverrideOptions staff() {
            OverrideOptions options = new OverrideOptions();
            options.permission = "sparrow.spawn.staff";
            options.cooldown = "0s";
            options.warmup = new OverrideWarmupOptions();
            return options;
        }

        private SpawnConfig.PermissionOverride merge(SpawnConfig.Options base, String path) {
            if (this.permission.isBlank()) throw new IllegalArgumentException(path + ".permission is required");
            return new SpawnConfig.PermissionOverride(this.permission, new SpawnConfig.Options(
                    this.cooldown == null ? base.cooldown() : parseDuration(path + ".cooldown", this.cooldown),
                    this.warmup == null ? base.warmup() : this.warmup.toSettings(path + ".warmup")
            ));
        }
    }
}