package net.momirealms.sparrow.feature.ban;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class BanSettings implements FeatureSettings {
    private boolean enabled = true;

    @Comment({
            "Reason presets for /ban and /ban-ip, also offered in tab completion.",
            "An exact preset name expands to its message. Other input remains a custom reason. An empty map disables presets."
    })
    @Comment(
            lang = "zh",
            value = {
                    "/ban 与 /ban-ip 的原因预设, 同时提供名称补全.",
                    "原因完全匹配预设名称时展开为对应文本, 其他内容作为自定义原因. 设置为 {} 可关闭预设."
            }
    )
    @NotNull
    private Map<String, String> reasonPresets = Map.of("cheating", "Cheating", "grief", "Griefing");

    @Comment({
            "Ban notification sound. Accepts a mapping, [key, volume, pitch, source, seed], a comma-separated string or a sound key.",
            "Volume and pitch default to 1, source to MASTER, seed is optional. Empty string or zero volume disables the sound."
    })
    @Comment(
            lang = "zh",
            value = {
                    "封禁通知音效. 支持映射、[声音键, 音量, 音高, 声源, 种子]、逗号分隔字符串或单独声音键.",
                    "省略音量、音高时为 1, 声源为 MASTER, 种子可省略. 空字符串或音量为 0 时不播放."
            }
    )
    @NotNull
    private Sound notifyBanSound = Sound.sound(Key.key("entity.lightning_bolt.thunder"), Sound.Source.MASTER, 0.25f, 1.0f);

    @Comment("Unban notification sound. Accepts the same forms as notify-ban-sound.")
    @Comment(lang = "zh", value = "解封通知音效, 支持与 notify-ban-sound 相同的写法.")
    @NotNull
    private Sound notifyUnbanSound = Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 0.25f, 1.0f);

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    @NotNull
    public Map<String, String> reasonPresets() {
        return this.reasonPresets;
    }

    @NotNull
    public String getReason(@NotNull String input) {
        return this.reasonPresets.getOrDefault(input, input);
    }

    @Nullable
    public Sound getNotifySound(boolean banned) {
        Sound sound = banned ? this.notifyBanSound : this.notifyUnbanSound;
        return sound.volume() == 0.0f ? null : sound;
    }
}
