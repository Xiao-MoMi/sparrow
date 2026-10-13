package net.momirealms.sparrow.feature.tpa;

import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class TpaSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment("Seconds before a request that nobody answered expires.")
    @Comment(lang = "zh", value = "请求多少秒没有被处理就过期.")
    private int expireSeconds = 60;

    @Comment("Seconds a player waits between sending two requests. /tpa and /tpa-here share it. 0 disables it, sparrow.bypass.tpa-cooldown skips it.")
    @Comment(lang = "zh", value = "两次发起请求之间的间隔秒数, /tpa 与 /tpa-here 共用. 0 表示不限制, sparrow.bypass.tpa-cooldown 可跳过.")
    private int requestCooldownSeconds = 10;

    @Comment("Seconds a player cannot send another request to someone who denied them. 0 disables it, sparrow.bypass.tpa-cooldown skips it.")
    @Comment(lang = "zh", value = "被某位玩家拒绝后, 多少秒内不能再向他发起请求. 0 表示不限制, sparrow.bypass.tpa-cooldown 可跳过.")
    private int denyCooldownSeconds = 120;

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int expireSeconds() {
        return this.expireSeconds;
    }

    public int requestCooldownSeconds() {
        return this.requestCooldownSeconds;
    }

    public int denyCooldownSeconds() {
        return this.denyCooldownSeconds;
    }
}