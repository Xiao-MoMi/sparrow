package net.momirealms.sparrow.feature.highlight;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class HighlightSettings implements FeatureSettings {
    private boolean enabled = true;
    @Comment("Default named glow color, such as green, red or yellow.")
    @Comment(lang = "zh", value = "默认发光颜色名称, 如 green、red、yellow.")
    private String defaultColor = "green";

    @Comment("Display duration in seconds (0-300).")
    @Comment(lang = "zh", value = "高亮持续秒数 (0-300).")
    private int defaultDuration = 30;

    @Comment("Maximum number of blocks in the selection, including both endpoints.")
    @Comment(lang = "zh", value = "选区最多包含的方块数, 两端的方块也计入范围.")
    private int maxBlocks = 32768;

    @Comment("Only highlight non-passable blocks exposed to air or the selection boundary.")
    @Comment(lang = "zh", value = "只高亮不可穿过且邻接可穿过方块、或位于选区边界的方块.")
    private boolean solidOnly;

    @Comment("Seconds allowed to select both points.")
    @Comment(lang = "zh", value = "交互选点的超时秒数.")
    private int selectionTimeout = 30;

    @Override
    public boolean enabled() { return this.enabled; }

    @Override
    public void enabled(boolean enabled) { this.enabled = enabled; }

    @NotNull
    public String defaultColor() { return this.defaultColor; }

    public int defaultDuration() { return this.defaultDuration; }

    public int maxBlocks() { return this.maxBlocks; }

    public boolean solidOnly() { return this.solidOnly; }

    public int selectionTimeout() { return this.selectionTimeout; }
}
