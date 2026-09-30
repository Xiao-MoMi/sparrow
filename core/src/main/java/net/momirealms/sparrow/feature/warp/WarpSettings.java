package net.momirealms.sparrow.feature.warp;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.player.teleport.TeleportOptions;
import net.momirealms.sparrow.player.teleport.TeleportType;
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
    @Comment("Seconds a player must stand still before warping. sparrow.teleport-warmup.<seconds> overrides it (lowest node wins), sparrow.bypass.teleport-warmup skips it.")
    @Comment(lang = "zh", value = "传送前需要原地等待的秒数. sparrow.teleport-warmup.<秒> 可覆盖该值 (取最小的节点), sparrow.bypass.teleport-warmup 可跳过.")
    private int warmupSeconds = 3;

    @Comment("Seconds before a player can warp again, shared across servers. 0 disables it. sparrow.bypass.teleport-cooldown skips it.")
    @Comment(lang = "zh", value = "两次 warp 之间的冷却秒数, 各服务器共享. 0 表示不限制. sparrow.bypass.teleport-cooldown 可跳过.")
    private int cooldownSeconds = 0;

    @Comment("Cancel the warmup when the player moves or takes damage.")
    @Comment(lang = "zh", value = "预热期间移动或受伤时是否取消传送.")
    private boolean cancelOnMove = true;
    private boolean cancelOnDamage = true;

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
        return new TeleportOptions(TeleportType.WARP, this.warmupSeconds, this.cooldownSeconds, this.cancelOnMove, this.cancelOnDamage);
    }
}
