package net.momirealms.sparrow.feature.home;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.teleport.TeleportOptions;
import net.momirealms.sparrow.teleport.TeleportSettings;
import net.momirealms.sparrow.teleport.TeleportType;
import org.jetbrains.annotations.NotNull;
import java.util.regex.Pattern;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class HomeSettings implements FeatureSettings {
    private boolean enabled = true;

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
        this.teleport.validate();
        if (this.suggestionLimit < 1 || this.maxHomes < 0 || !this.validName(this.defaultName)) {
            throw new IllegalArgumentException("Invalid home settings: check limits, timings and default-name");
        }
    }

    @NotNull
    public TeleportOptions teleportOptions() {
        return this.teleport.createOptions(TeleportType.HOME);
    }
}