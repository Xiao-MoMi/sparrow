package net.momirealms.sparrow.plugin.configuration.serializer;

import net.momirealms.sparrow.teleport.search.TeleportSearchOptions;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TeleportSearchSerializer {
    private final SparrowYaml yaml;

    public TeleportSearchSerializer(@NotNull SparrowYaml yaml) {
        this.yaml = yaml;
    }

    @NotNull
    public NodeSerializer<TeleportSearchOptions> serializer() {
        return NodeSerializers.OBJECT.xmap(this::read, this::write);
    }

    private TeleportSearchOptions read(Object value) {
        Map<?, ?> options = (Map<?, ?>) value;
        Object type = options.containsKey("type") ? options.get("type") : "direct";
        Class<? extends TeleportSearchOptions> implementation = switch (String.valueOf(type)) {
            case "direct" -> TeleportSearchOptions.Direct.class;
            case "nearby" -> TeleportSearchOptions.Nearby.class;
            case "random" -> TeleportSearchOptions.Random.class;
            default -> throw new IllegalArgumentException("Unknown teleport search type: " + type);
        };
        YamlDocument document = this.yaml.createDocument();
        options.forEach(document::setSubNode);
        return this.yaml.serializers().register(implementation).deserialize(document);
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> write(TeleportSearchOptions search) {
        String type = switch (search) {
            case TeleportSearchOptions.Direct ignored -> "direct";
            case TeleportSearchOptions.Nearby ignored -> "nearby";
            case TeleportSearchOptions.Random ignored -> "random";
        };
        NodeSerializer<TeleportSearchOptions> serializer = (NodeSerializer<TeleportSearchOptions>) this.yaml.serializers().register(search.getClass());
        Map<Object, Object> options = new LinkedHashMap<>();
        options.put("type", type);
        options.putAll((Map<Object, Object>) serializer.serialize(search));
        return options;
    }
}