package net.momirealms.sparrow.feature.quickshulker;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class QuickShulkerSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Require sneaking when right-clicking to open the shulker box.")
    @Comment(lang = "zh", value = "是否需要潜行才能右键打开手持的潜影盒.")
    private boolean requireSneaking = true;

    @Comment("Allow right-clicking to open a shulker box held in the off hand.")
    @Comment(lang = "zh", value = "是否允许右键打开副手持有的潜影盒.")
    private boolean allowOffhand = true;

    @Comment("Container title. <arg:hover_name> displays the name of the held shulker box.")
    @Comment(lang = "zh", value = "容器标题, <arg:hover_name> 表示手持潜影盒的物品名称.")
    private String title = "<arg:hover_name>";

    @Comment("World names where quick shulker access is disabled. An empty list allows all worlds.")
    @Comment(lang = "zh", value = "禁用潜影盒快捷打开功能的世界名称, 空列表表示所有世界均可使用.")
    private List<String> disabledWorlds = List.of();

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean requireSneaking() {
        return this.requireSneaking;
    }

    public boolean allowOffhand() {
        return this.allowOffhand;
    }

    @NotNull
    public String title() {
        return this.title;
    }

    @NotNull
    public List<String> disabledWorlds() {
        return this.disabledWorlds;
    }
}
