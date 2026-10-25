package net.momirealms.sparrow.plugin.configuration.serializer;

import net.momirealms.sparrow.teleport.processor.post.Invulnerability;
import net.momirealms.sparrow.teleport.processor.post.PlaySound;
import net.momirealms.sparrow.teleport.processor.post.PostProcessor;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessor;
import net.momirealms.sparrow.teleport.profile.ConditionFactory;
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
import java.util.Map;
import java.util.Set;

public final class TeleportProfileSerializer {
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
        // 每段一张效果表, 键是配置里写的效果名
        Map<String, NodeSerializer<? extends ConditionFactory>> beforeEffects = Map.of();
        Map<String, NodeSerializer<? extends PositionProcessor>> targetEffects = Map.of();
        Map<String, NodeSerializer<? extends PostProcessor>> afterEffects = Map.of("invulnerability", invulnerability, "sound", sound);
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

    // 纯数字按秒, 其余按 DurationUtils 的写法, 允许 0
    private static Duration parseDuration(String text) {
        if (text.chars().allMatch(c -> c >= '0' && c <= '9')) return Duration.ofSeconds(Long.parseLong(text));
        return DurationUtils.parseNonNegative(text);
    }
}