package net.momirealms.sparrow.feature.maintenance;

import net.minecraft.world.BossEvent;
import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class MaintenanceSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Whether the server is currently under maintenance. Saved automatically by /maintenance.")
    @Comment(lang = "zh", value = "服务器当前是否处于维护状态, 使用 /maintenance 切换时自动保存.")
    private boolean active = false;

    private BossBarOptions bossBar = new BossBarOptions();

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean active() {
        return this.active;
    }

    public void active(boolean active) {
        this.active = active;
    }

    @NotNull
    public BossBarOptions bossBar() {
        return this.bossBar;
    }

    // 配置错误在启用前报告.
    void validate() {
        this.bossBar.color();
        this.bossBar.overlay();
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class BossBarOptions {
        @Comment("Show a boss bar to online players during maintenance. The text is set in the translation files.")
        @Comment(lang = "zh", value = "维护期间向在线玩家显示 BossBar, 文本在语言文件中修改.")
        private boolean enabled = true;

        @Comment("Options: PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE.")
        @Comment(lang = "zh", value = "可选 PINK、BLUE、RED、GREEN、YELLOW、PURPLE、WHITE.")
        private String color = "RED";

        @Comment("Options: PROGRESS, NOTCHED_6, NOTCHED_10, NOTCHED_12, NOTCHED_20.")
        @Comment(lang = "zh", value = "可选 PROGRESS、NOTCHED_6、NOTCHED_10、NOTCHED_12、NOTCHED_20.")
        private String overlay = "PROGRESS";

        public boolean enabled() {
            return this.enabled;
        }

        @NotNull
        public BossEvent.BossBarColor color() {
            return BossEvent.BossBarColor.valueOf(this.color.toUpperCase(Locale.ROOT));
        }

        @NotNull
        public BossEvent.BossBarOverlay overlay() {
            return BossEvent.BossBarOverlay.valueOf(this.overlay.toUpperCase(Locale.ROOT));
        }
    }
}
