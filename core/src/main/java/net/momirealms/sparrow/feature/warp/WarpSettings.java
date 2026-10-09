package net.momirealms.sparrow.feature.warp;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.teleport.TeleportOptions;
import net.momirealms.sparrow.teleport.TeleportSettings;
import net.momirealms.sparrow.teleport.TeleportType;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class WarpSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Require the sparrow.warp.<name> permission to see and use each warp (lowercase name). sparrow.warp.* grants all warps.")
    @Comment(lang = "zh", value = "开启后, 玩家需要 sparrow.warp.<名称> 权限才能看到和使用对应的 warp (名称用小写). sparrow.warp.* 表示全部 warp.")
    private boolean permissionRestrict = false;

    @Comment("When /set-warp uses an existing name, move that warp to the new location. When false, the command is refused.")
    @Comment(lang = "zh", value = "/set-warp 使用已存在的名称时, 是否把该 warp 移到新位置. 关闭时拒绝执行.")
    private boolean overwriteExisting = true;

    @Comment("Allowed warp names. By default letters (including Chinese), digits, _ and -, not starting with -. Names are at most 32 characters.")
    @Comment(lang = "zh", value = "允许的 warp 名称. 默认允许字母 (包括中文)、数字、_ 和 -, 不能以 - 开头. 名称最长 32 个字.")
    private String namePattern = "[\\p{L}\\p{N}_][\\p{L}\\p{N}_-]*";

    @Comment("How many names tab completion shows at most. Typing more characters narrows the list.")
    @Comment(lang = "zh", value = "按 Tab 补全时最多显示多少个名称.")
    private int suggestionLimit = 100;

    @BlankLineBefore
    @Comment("Teleport warmup, cooldown, display and sounds.")
    @Comment(lang = "zh", value = "传送预热、冷却、显示和音效.")
    private TeleportSettings teleport = new TeleportSettings();

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean permissionRestrict() {
        return this.permissionRestrict;
    }

    public boolean overwriteExisting() {
        return this.overwriteExisting;
    }

    @NotNull
    public String namePattern() {
        return this.namePattern;
    }

    public int suggestionLimit() {
        return this.suggestionLimit;
    }

    // 玩家自己传送时的默认参数, 实际参数由 TeleportOptions.resolve 按权限与命令参数调整
    @NotNull
    public TeleportOptions teleportOptions() {
        return this.teleport.createOptions(TeleportType.WARP);
    }
}