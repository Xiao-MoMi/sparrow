package net.momirealms.sparrow.plugin.configuration.serializer;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.teleport.CooldownProcessor;
import net.momirealms.sparrow.teleport.SoundProcessor;
import net.momirealms.sparrow.teleport.TeleportProcessor;
import net.momirealms.sparrow.teleport.WarmupProcessor;
import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.serializer.NodeSerializer;
import net.momirealms.sparrow.yaml.serializer.NodeSerializers;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TeleportProcessorSerializer {
    private static final String TYPE_KEY = "processor-type";
    private static final Map<String, Class<? extends TeleportProcessor>> TYPES = Map.of(
            "cooldown", CooldownProcessor.class,
            "warmup", WarmupProcessor.class,
            "sound", SoundProcessor.class
    );

    private final SparrowYaml sparrowYaml;
    private int issues; // 上次取走之后被忽略的处理器数量

    public TeleportProcessorSerializer(@NotNull SparrowYaml sparrowYaml) {
        this.sparrowYaml = sparrowYaml;
    }

    // 读取某个阶段的处理器列表. 写错的项不进入列表, 只在控制台警告并计入问题数量
    @NotNull
    public <T extends TeleportProcessor> NodeSerializer<List<T>> serializer(@NotNull Class<T> stage) {
        return NodeSerializers.OBJECT_LIST.xmap(
                entries -> {
                    List<T> processors = new ArrayList<>(entries.size());
                    for (Object entry : entries) {
                        try {
                            processors.add(this.read(stage, entry));
                        } catch (RuntimeException exception) {
                            this.issues++;
                            SparrowPlugin.instance().logger().warn("Ignored teleport processor " + entry + " in teleport.yml: " + exception.getMessage());
                        }
                    }
                    return List.copyOf(processors);
                },
                processors -> {
                    List<Object> entries = new ArrayList<>(processors.size());
                    for (T processor : processors) {
                        entries.add(this.write(processor));
                    }
                    return entries;
                }
        );
    }

    public int takeIssues() {
        int issues = this.issues;
        this.issues = 0;
        return issues;
    }

    // 按 processor-type 读成对应的处理器, 其余的键是这个处理器自己的选项
    private <T extends TeleportProcessor> T read(Class<T> stage, Object entry) {
        Map<?, ?> options = (Map<?, ?>) entry;
        String type = String.valueOf(options.get(TYPE_KEY));
        Class<? extends TeleportProcessor> processorType = TYPES.get(type);
        if (processorType == null) throw new IllegalArgumentException("unknown processor type " + type);
        if (!stage.isAssignableFrom(processorType)) {
            throw new IllegalArgumentException(type + " cannot be used as a " + stage.getSimpleName().toLowerCase(Locale.ROOT) + "-processor");
        }
        // 选项放进一份空文档, 交给处理器自己的序列化器读取
        YamlDocument document = this.sparrowYaml.createDocument();
        options.forEach(document::setSubNode);
        T processor = stage.cast(this.sparrowYaml.serializers().register(processorType).deserialize(document));
        processor.validate();
        return processor;
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> write(TeleportProcessor processor) {
        NodeSerializer<TeleportProcessor> serializer = (NodeSerializer<TeleportProcessor>) this.sparrowYaml.serializers().register(processor.getClass());
        Map<Object, Object> entry = new LinkedHashMap<>();
        entry.put(TYPE_KEY, typeOf(processor.getClass()));
        entry.putAll((Map<Object, Object>) serializer.serialize(processor));
        return entry;
    }

    private static String typeOf(Class<?> processorType) {
        for (Map.Entry<String, Class<? extends TeleportProcessor>> type : TYPES.entrySet()) {
            if (type.getValue() == processorType) return type.getKey();
        }
        throw new IllegalArgumentException("Unregistered processor: " + processorType.getName());
    }
}