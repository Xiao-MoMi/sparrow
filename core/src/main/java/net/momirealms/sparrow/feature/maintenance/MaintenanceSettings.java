package net.momirealms.sparrow.feature.maintenance;

import net.minecraft.world.BossEvent;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class MaintenanceSettings {
    @Comment("Whether the server is currently under maintenance. Saved automatically by /maintenance.")
    @Comment(lang = "zh", value = "服务器当前是否处于维护状态, 使用 /maintenance 切换时自动保存.")
    private boolean active = false;

    private BossBarOptions bossBar = new BossBarOptions();

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

    void validate() {
        this.bossBar.getColor();
        this.bossBar.getOverlay();
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    public static final class BossBarOptions {
        @Comment("Show a boss bar to online players during maintenance. The text is set in the translation files.")
        @Comment(lang = "zh", value = "维护期间向在线玩家显示 BossBar, 文本在语言文件中修改.")
        private boolean enabled = true;

        @Comment("Options: pink, blue, red, green, yellow, purple, white.")
        @Comment(lang = "zh", value = "可选 pink、blue、red、green、yellow、purple、white.")
        private String color = "red";

        @Comment("Options: progress, notched_6, notched_10, notched_12, notched_20.")
        @Comment(lang = "zh", value = "可选 progress、notched_6、notched_10、notched_12、notched_20.")
        private String overlay = "progress";

        public boolean enabled() {
            return this.enabled;
        }

        @NotNull
        public BossEvent.BossBarColor getColor() {
            return BossEvent.BossBarColor.valueOf(this.color.toUpperCase(Locale.ROOT));
        }

        @NotNull
        public BossEvent.BossBarOverlay getOverlay() {
            return BossEvent.BossBarOverlay.valueOf(this.overlay.toUpperCase(Locale.ROOT));
        }
    }
}