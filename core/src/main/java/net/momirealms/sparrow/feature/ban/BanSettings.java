package net.momirealms.sparrow.feature.ban;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.BlankLineBefore;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;

import java.util.Map;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class BanSettings {
    @Comment("Exact preset names expand to the configured reason. Other input is kept as custom text.")
    @Comment(lang = "zh", value = "原因完整匹配预设名称时展开, 其他输入作为自定义原因. 自定义预设名称使用下划线.")
    private Map<String, String> reasonPresets = Map.ofEntries(
            Map.entry("cheating", "使用作弊程序"),
            Map.entry("malicious_damage", "恶意破坏"),
            Map.entry("inappropriate_behavior", "不当行为")
    );

    @BlankLineBefore
    @Comment("Ban notification sound. An empty string or zero volume disables the sound.")
    @Comment(lang = "zh", value = "封禁通知音效, 空字符串或零音量关闭音效.")
    private Sound notifyBanSound = Sound.sound(Key.key("entity.lightning_bolt.thunder"), Sound.Source.MASTER, 0.25F, 1.0F);
    @Comment("Unban notification sound. Accepts the same formats as notify-ban-sound.")
    @Comment(lang = "zh", value = "解封通知音效, 格式与 notify-ban-sound 相同.")
    private Sound notifyUnbanSound = Sound.sound(Key.key("entity.experience_orb.pickup"), Sound.Source.MASTER, 0.25F, 1.0F);

    BanConfig toConfig() {
        for (Map.Entry<String, String> preset : this.reasonPresets.entrySet()) {
            String reason = preset.getValue();
            if (reason.codePointCount(0, reason.length()) > BanRecord.MAX_REASON_LENGTH) {
                throw new IllegalArgumentException("reason-presets." + preset.getKey() + " exceeds " + BanRecord.MAX_REASON_LENGTH + " characters");
            }
        }
        return new BanConfig(Map.copyOf(this.reasonPresets), this.notifyBanSound, this.notifyUnbanSound);
    }
}