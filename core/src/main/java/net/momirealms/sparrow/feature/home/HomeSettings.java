package net.momirealms.sparrow.feature.home;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.player.teleport.TeleportOptions;
import net.momirealms.sparrow.player.teleport.TeleportType;
import org.jetbrains.annotations.NotNull;
import java.util.regex.Pattern;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class HomeSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Seconds before online home data is refreshed from the database on its next use. Must be positive.")
    @Comment(lang = "zh", value = "在线玩家的 Home 缓存有效秒数, 过期后在下次使用时读库刷新. 必须大于 0.")
    private int cacheTtlSeconds = 300;

    @Comment("Default home name used by /set-home and preferred by /home.")
    @Comment(lang = "zh", value = "/set-home 的默认名称, /home 优先前往这个家.")
    private String defaultName = "home";

    @Comment("Default home limit. sparrow.max-homes.<number> overrides it; sparrow.max-homes.unlimited removes it.")
    @Comment(lang = "zh", value = "默认 Home 上限. sparrow.max-homes.<数字> 覆盖此值, sparrow.max-homes.unlimited 表示无限.")
    private int maxHomes = 3;

    @Comment("Allowed names, up to 32 characters. Whitespace, dots and a leading - are always forbidden.")
    @Comment(lang = "zh", value = "名称规则, 最长 32 个字符. 始终禁止空白、点号和以 - 开头.")
    private String namePattern = "[\\p{L}\\p{N}_][\\p{L}\\p{N}_-]*";
    private int suggestionLimit = 100;

    @Comment("Teleport warmup and cooldown in seconds. Home cooldowns are shared across servers and separate from Warp.")
    @Comment(lang = "zh", value = "传送预热与冷却秒数. Home 冷却跨服共享, 与 Warp 分开计算.")
    private int warmupSeconds = 3;
    private int cooldownSeconds = 0;
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

    public int cacheTtlSeconds() {
        return this.cacheTtlSeconds;
    }

    @NotNull
    public String defaultName() {
        return this.defaultName;
    }

    public int maxHomes() {
        return this.maxHomes;
    }

    public int suggestionLimit() {
        return this.suggestionLimit;
    }

    public boolean validName(@NotNull String name) {
        return !name.isEmpty() && name.length() <= Home.MAX_NAME_LENGTH && !name.startsWith("-") && !name.contains(".")
                && name.codePoints().noneMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)) && Pattern.matches(this.namePattern, name);
    }

    public void validate() {
        Pattern.compile(this.namePattern);
        if (this.cacheTtlSeconds < 1 || this.suggestionLimit < 1 || this.maxHomes < 0 || this.warmupSeconds < 0 || this.cooldownSeconds < 0 || !this.validName(this.defaultName)) {
            throw new IllegalArgumentException("Invalid home settings: check limits, timings and default-name");
        }
    }

    @NotNull
    public TeleportOptions teleportOptions() {
        return new TeleportOptions(TeleportType.HOME, this.warmupSeconds, this.cooldownSeconds, this.cancelOnMove, this.cancelOnDamage);
    }
}