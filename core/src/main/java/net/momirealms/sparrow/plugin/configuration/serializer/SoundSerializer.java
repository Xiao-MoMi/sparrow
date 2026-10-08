package net.momirealms.sparrow.plugin.configuration.serializer;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

public final class SoundSerializer {
    public static final SoundSerializer INSTANCE = new SoundSerializer();

    private final Sound disabledSound = Sound.sound(Key.key("intentionally_empty"), Sound.Source.MASTER, 0.0f, 1.0f);
    private final NodeSerializer<Sound> serializer;

    private SoundSerializer() {
        NodeSerializer<Key> keySerializer = KeySerializer.INSTANCE.serializer();
        NodeSerializer<Sound.Source> sourceSerializer = NodeSerializers.enumCodec(Sound.Source.class);
        this.serializer = NodeSerializers.mapping(Sound.class)
                .group(
                        keySerializer.required("key").forGetter(Sound::name),
                        NodeSerializers.FLOAT.optional("volume", 1.0f).forGetter(Sound::volume),
                        NodeSerializers.FLOAT.optional("pitch", 1.0f).forGetter(Sound::pitch),
                        sourceSerializer.optional("source", Sound.Source.MASTER).forGetter(Sound::source),
                        NodeSerializers.LONG.optional("seed").forGetter(this::getSeed)
                )
                .apply(this::create)
                .withAlternative(
                        NodeSerializers.sequence(Sound.class)
                        .group(
                                keySerializer.required(0).forGetter(Sound::name),
                                NodeSerializers.FLOAT.optional(1, 1.0f).forGetter(Sound::volume),
                                NodeSerializers.FLOAT.optional(2, 1.0f).forGetter(Sound::pitch),
                                sourceSerializer.optional(3, Sound.Source.MASTER).forGetter(Sound::source),
                                NodeSerializers.LONG.optional(4).forGetter(this::getSeed)
                        )
                        .apply(this::create)
                )
                .withAlternative(NodeSerializers.STRING, this::fromCsv)
                .withAlternative(NodeSerializers.STRING, this::fromKey);
    }

    @NotNull
    public NodeSerializer<Sound> serializer() {
        return this.serializer;
    }

    @NotNull
    private Sound create(@NotNull Key key, float volume, float pitch, @NotNull Sound.Source source, @NotNull Optional<Long> seed) {
        return Sound.sound()
                .type(key)
                .volume(volume)
                .pitch(pitch)
                .source(source)
                .seed(seed.map(OptionalLong::of).orElseGet(OptionalLong::empty))
                .build();
    }

    @NotNull
    private Optional<Long> getSeed(@NotNull Sound sound) {
        OptionalLong seed = sound.seed();
        return seed.isPresent() ? Optional.of(seed.getAsLong()) : Optional.empty();
    }

    @NotNull
    private Sound fromCsv(@NotNull String value) {
        String[] parts = value.split(",", -1);
        if (parts.length < 2 || parts.length > 5) {
            throw new IllegalArgumentException("Expected key,volume[,pitch,source,seed]");
        }
        Key key = Key.key(parts[0].trim());
        float volume = Float.parseFloat(parts[1].trim());
        float pitch = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1.0f;
        Sound.Source source = parts.length > 3 ? Sound.Source.valueOf(parts[3].trim().toUpperCase(Locale.ROOT)) : Sound.Source.MASTER;
        Optional<Long> seed = parts.length > 4 ? Optional.of(Long.parseLong(parts[4].trim())) : Optional.empty();
        return this.create(key, volume, pitch, source, seed);
    }

    @NotNull
    private Sound fromKey(@NotNull String value) {
        String key = value.trim();
        return key.isEmpty() ? this.disabledSound : Sound.sound(Key.key(key), Sound.Source.MASTER, 1.0f, 1.0f);
    }
}