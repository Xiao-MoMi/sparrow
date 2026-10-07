package net.momirealms.sparrow.plugin.configuration.serializer;

import net.kyori.adventure.key.Key;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import org.jetbrains.annotations.NotNull;

public final class KeySerializer {
    public static final KeySerializer INSTANCE = new KeySerializer();

    private final NodeSerializer<Key> serializer;

    private KeySerializer() {
        this.serializer = NodeSerializers.STRING.xmap(Key.class, Key::key, Key::asString);
    }

    @NotNull
    public NodeSerializer<Key> serializer() {
        return this.serializer;
    }
}