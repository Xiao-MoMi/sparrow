package net.momirealms.sparrow.plugin.configuration.serializer;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minecraft.world.BossEvent;
import net.momirealms.sparrow.service.countdown.CountdownDisplay;
import net.momirealms.sparrow.service.countdown.CountdownSettings;
import net.momirealms.sparrow.teleport.condition.ConditionFactory;
import net.momirealms.sparrow.teleport.condition.CooldownCondition;
import net.momirealms.sparrow.teleport.condition.WarmupCondition;
import net.momirealms.sparrow.teleport.processor.post.Invulnerability;
import net.momirealms.sparrow.teleport.processor.post.PlaySound;
import net.momirealms.sparrow.teleport.processor.post.PostProcessor;
import net.momirealms.sparrow.teleport.processor.post.SetCooldown;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessor;
import net.momirealms.sparrow.teleport.profile.ProfileVariant;
import net.momirealms.sparrow.teleport.profile.TeleportProfile;
import net.momirealms.sparrow.teleport.profile.TeleportProfiles;
import net.momirealms.sparrow.util.DurationUtils;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TeleportProfileSerializer {
    private static final CountdownSettings WARMUP_DEFAULTS = new CountdownSettings( // warmup 没写的选项取这里的值
            Duration.ZERO,
            true,
            true,
            CountdownDisplay.ACTION_BAR,
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS,
            Sound.sound(Key.key("block.note_block.banjo"), Sound.Source.MASTER, 1.0F, 1.0F),
            Sound.sound(Key.key("entity.item.break"), Sound.Source.MASTER, 1.0F, 1.0F)
    );
    public static final TeleportProfileSerializer INSTANCE = new TeleportProfileSerializer();

    private final NodeSerializer<TeleportProfiles> serializer;

    private TeleportProfileSerializer() {
        NodeSerializer<Duration> duration = NodeSerializers.scalar(Duration.class, TeleportProfileSerializer::parseDuration, value -> DurationUtils.format(value.toMillis()));
        // 效果只写一个值时设置主选项, 编码也写成这种简写
        NodeSerializer<Invulnerability> invulnerabilityMapping = NodeSerializers.mapping(Invulnerability.class)
                .group(duration.required("duration").forGetter(effect -> Duration.ofSeconds(effect.seconds())))
                .apply(value -> new Invulnerability((int) value.toSeconds()));
        NodeSerializer<Invulnerability> invulnerability = duration.xmap(Invulnerability.class, value -> new Invulnerability((int) value.toSeconds()), effect -> Duration.ofSeconds(effect.seconds()))
                .withAlternative(invulnerabilityMapping);
        NodeSerializer<PlaySound> sound = SoundSerializer.INSTANCE.serializer().xmap(PlaySound.class, PlaySound::new, PlaySound::sound);
        NodeSerializer<WarmupCondition> warmup = NodeSerializers.mapping(WarmupCondition.class)
                .group(
                        duration.required("duration").forGetter(effect -> effect.settings().duration()),
                        NodeSerializers.BOOLEAN.optional("cancel_on_move", WARMUP_DEFAULTS.cancelOnMove()).forGetter(effect -> effect.settings().cancelOnMove()),
                        NodeSerializers.BOOLEAN.optional("cancel_on_damage", WARMUP_DEFAULTS.cancelOnDamage()).forGetter(effect -> effect.settings().cancelOnDamage()),
                        lowerEnum(CountdownDisplay.class).optional("display", WARMUP_DEFAULTS.display()).forGetter(effect -> effect.settings().display()),
                        lowerEnum(BossEvent.BossBarColor.class).optional("boss_bar_color", WARMUP_DEFAULTS.bossBarColor()).forGetter(effect -> effect.settings().bossBarColor()),
                        lowerEnum(BossEvent.BossBarOverlay.class).optional("boss_bar_overlay", WARMUP_DEFAULTS.bossBarOverlay()).forGetter(effect -> effect.settings().bossBarOverlay()),
                        SoundSerializer.INSTANCE.serializer().optional("tick_sound", WARMUP_DEFAULTS.tickSound()).forGetter(effect -> effect.settings().tickSound()),
                        SoundSerializer.INSTANCE.serializer().optional("cancel_sound", WARMUP_DEFAULTS.cancelSound()).forGetter(effect -> effect.settings().cancelSound())
                )
                .apply((value, cancelOnMove, cancelOnDamage, display, bossBarColor, bossBarOverlay, tickSound, cancelSound) -> new WarmupCondition(
                        new CountdownSettings(value, cancelOnMove, cancelOnDamage, display, bossBarColor, bossBarOverlay, tickSound, cancelSound)
                ))
                .withAlternative(duration, value -> new WarmupCondition(new CountdownSettings(
                        value,
                        WARMUP_DEFAULTS.cancelOnMove(),
                        WARMUP_DEFAULTS.cancelOnDamage(),
                        WARMUP_DEFAULTS.display(),
                        WARMUP_DEFAULTS.bossBarColor(),
                        WARMUP_DEFAULTS.bossBarOverlay(),
                        WARMUP_DEFAULTS.tickSound(),
                        WARMUP_DEFAULTS.cancelSound()
                )));
        // 冷却分成两半: before 段只检查, after 段在到达后开始计时; 两处的 id 都必须写
        NodeSerializer<CooldownCondition> cooldownCheck = NodeSerializers.STRING.xmap(CooldownCondition.class, CooldownCondition::new, CooldownCondition::id)
                .withAlternative(NodeSerializers.mapping(CooldownCondition.class)
                        .group(NodeSerializers.STRING.required("id").forGetter(CooldownCondition::id))
                        .apply(CooldownCondition::new));
        NodeSerializer<SetCooldown> cooldownStart = NodeSerializers.mapping(SetCooldown.class)
                .group(
                        NodeSerializers.STRING.required("id").forGetter(SetCooldown::id),
                        duration.required("duration").forGetter(SetCooldown::duration)
                )
                .apply(SetCooldown::new);
        // 每段一张效果表, 键是配置里写的效果名
        Map<String, NodeSerializer<? extends ConditionFactory>> beforeEffects = Map.of("warmup", warmup, "cooldown", cooldownCheck);
        Map<String, NodeSerializer<? extends PositionProcessor>> targetEffects = Map.of();
        Map<String, NodeSerializer<? extends PostProcessor>> afterEffects = Map.of("invulnerability", invulnerability, "sound", sound, "cooldown", cooldownStart);
        NodeSerializer<Map<String, ConditionFactory>> before = stage("before", beforeEffects);
        NodeSerializer<Map<String, PositionProcessor>> target = stage("target", targetEffects);
        NodeSerializer<Map<String, PostProcessor>> after = stage("after", afterEffects);
        // 方案和覆盖条目同样按键分派, 写错的键会直接报错
        Map<String, NodeSerializer<?>> overrideFields = Map.of("permission", NodeSerializers.STRING, "before", before, "target", target, "after", after);
        NodeSerializer<TeleportProfile.PermissionOverride> override = NodeSerializers.<String, Object>dispatchedMap(key("override field", overrideFields.keySet()), overrideFields::get)
                .xmap(TeleportProfile.PermissionOverride.class, TeleportProfileSerializer::readOverride, TeleportProfileSerializer::writeOverride);
        Map<String, NodeSerializer<?>> profileFields = Map.of("before", before, "target", target, "after", after, "overrides", override.listOf());
        NodeSerializer<TeleportProfile> profile = NodeSerializers.<String, Object>dispatchedMap(key("profile field", profileFields.keySet()), profileFields::get)
                .xmap(TeleportProfile.class, TeleportProfileSerializer::readProfile, TeleportProfileSerializer::writeProfile);
        this.serializer = profile.mapOf().xmap(TeleportProfiles.class, TeleportProfiles::new, TeleportProfiles::profiles);
    }

    @NotNull
    public NodeSerializer<TeleportProfiles> serializer() {
        return this.serializer;
    }

    @SuppressWarnings("unchecked")
    private static TeleportProfile readProfile(Map<String, Object> fields) {
        ProfileVariant base = new ProfileVariant(
                (Map<String, ConditionFactory>) fields.getOrDefault("before", Map.of()),
                (Map<String, PositionProcessor>) fields.getOrDefault("target", Map.of()),
                (Map<String, PostProcessor>) fields.getOrDefault("after", Map.of())
        );
        return new TeleportProfile(base, (List<TeleportProfile.PermissionOverride>) fields.getOrDefault("overrides", List.of()));
    }

    // 空段和空覆盖列表不写出
    private static Map<String, Object> writeProfile(TeleportProfile profile) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ProfileVariant base = profile.base();
        if (!base.before().isEmpty()) fields.put("before", base.before());
        if (!base.target().isEmpty()) fields.put("target", base.target());
        if (!base.after().isEmpty()) fields.put("after", base.after());
        if (!profile.overrides().isEmpty()) fields.put("overrides", profile.overrides());
        return fields;
    }

    // 没写的段保持为 null, 写了 {} 的段是空段
    @SuppressWarnings("unchecked")
    private static TeleportProfile.PermissionOverride readOverride(Map<String, Object> fields) {
        String permission = (String) fields.get("permission");
        if (permission == null) throw new IllegalArgumentException("Teleport profile override requires a permission");
        return new TeleportProfile.PermissionOverride(
                permission,
                (Map<String, ConditionFactory>) fields.get("before"),
                (Map<String, PositionProcessor>) fields.get("target"),
                (Map<String, PostProcessor>) fields.get("after")
        );
    }

    private static Map<String, Object> writeOverride(TeleportProfile.PermissionOverride override) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("permission", override.permission());
        if (override.before() != null) fields.put("before", override.before());
        if (override.target() != null) fields.put("target", override.target());
        if (override.after() != null) fields.put("after", override.after());
        return fields;
    }

    private static <T> NodeSerializer<Map<String, T>> stage(String name, Map<String, NodeSerializer<? extends T>> effects) {
        return NodeSerializers.dispatchedMap(key(name + " effect", effects.keySet()), effects::get);
    }

    private static NodeSerializer<String> key(String kind, Set<String> keys) {
        return NodeSerializers.scalar(String.class, key -> {
            if (!keys.contains(key)) throw new IllegalArgumentException("Unknown " + kind + ": " + key);
            return key;
        }, key -> key);
    }

    // 枚举值写成小写, 读取时不区分大小写
    private static <E extends Enum<E>> NodeSerializer<E> lowerEnum(Class<E> type) {
        return NodeSerializers.scalar(type, value -> Enum.valueOf(type, value.toUpperCase(Locale.ROOT)), value -> value.name().toLowerCase(Locale.ROOT));
    }

    // 纯数字按秒, 其余按 DurationUtils 的写法, 允许 0
    private static Duration parseDuration(String text) {
        if (text.chars().allMatch(c -> c >= '0' && c <= '9')) return Duration.ofSeconds(Long.parseLong(text));
        return DurationUtils.parseNonNegative(text);
    }
}