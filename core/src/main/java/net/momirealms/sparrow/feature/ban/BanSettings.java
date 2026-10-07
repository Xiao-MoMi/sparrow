package net.momirealms.sparrow.feature.ban;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.momirealms.sparrow.feature.FeatureSettings;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class BanSettings implements FeatureSettings {
    private boolean enabled = true;

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
    private @NotNull Sound notifyBanSound = Sound.sound(Key.key("entity.lightning_bolt.thunder"), Sound.Source.MASTER, 0.25f, 1.0f);

    @Comment("Unban notification sound. Accepts the same forms as notify-ban-sound.")
    @Comment(lang = "zh", value = "解封通知音效, 支持与 notify-ban-sound 相同的写法.")
    private @NotNull Sound notifyUnbanSound = Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 0.25f, 1.0f);

    @Override
    public boolean enabled() {
        return this.enabled;
    }

    @Override
    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Nullable
    public Sound getNotifySound(boolean banned) {
        Sound sound = banned ? this.notifyBanSound : this.notifyUnbanSound;
        return sound.volume() == 0.0f ? null : sound;
    }
}