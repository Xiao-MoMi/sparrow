package net.momirealms.sparrow.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class DataTreeRenderer {
    private static final TextColor TEXT_COLOR = TextColor.color(0xF5F5F5);

    private DataTreeRenderer() {}

    @NotNull
    public static List<Component> render(@NotNull Map<String, Object> data, boolean copyable) {
        List<Component> lines = new ArrayList<>();
        appendMap(data, lines, 0, false, copyable);
        return lines;
    }

    private static void appendMap(Map<?, ?> map, List<Component> lines, int depth, boolean listItem, boolean copyable) {
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            String type = value instanceof Map<?, ?> ? "Map" : value instanceof List<?> ? "List" : value.getClass().getSimpleName();
            Component key = Component.text(visible(entry.getKey().toString()), NamedTextColor.GOLD)
                    .hoverEvent(Component.text(type, NamedTextColor.YELLOW));
            Component prefix = Component.text(listItem && first ? "  ".repeat(depth - 1) + "- " : "  ".repeat(depth), TEXT_COLOR)
                    .append(key).append(Component.text(":", TEXT_COLOR));
            appendValue(value, prefix, lines, depth, copyable);
            first = false;
        }
    }

    private static void appendValue(Object value, Component prefix, List<Component> lines, int depth, boolean copyable) {
        if (value instanceof Map<?, ?> map && !map.isEmpty()) {
            lines.add(prefix);
            appendMap(map, lines, depth + 1, false, copyable);
        } else if (value instanceof List<?> list && !list.isEmpty()) {
            lines.add(prefix);
            int size = list.size();
            for (int i = 0; i < size; i++) {
                Object element = list.get(i);
                if (element instanceof Map<?, ?> map && !map.isEmpty()) {
                    appendMap(map, lines, depth + 2, true, copyable);
                } else {
                    appendValue(element, Component.text("  ".repeat(depth + 1) + "-", TEXT_COLOR), lines, depth + 1, copyable);
                }
            }
        } else {
            String text = switch (value) {
                case Object[] array -> Arrays.deepToString(array);
                case byte[] array -> Arrays.toString(array);
                case int[] array -> Arrays.toString(array);
                case long[] array -> Arrays.toString(array);
                case short[] array -> Arrays.toString(array);
                case float[] array -> Arrays.toString(array);
                case double[] array -> Arrays.toString(array);
                case boolean[] array -> Arrays.toString(array);
                case char[] array -> Arrays.toString(array);
                default -> value.toString();
            };
            Component component = Component.text(visible(text), TEXT_COLOR);
            if (copyable) {
                component = component.hoverEvent(Component.translatable("chat.copy.click", NamedTextColor.WHITE))
                        .clickEvent(ClickEvent.copyToClipboard(text));
            }
            lines.add(prefix.append(Component.space()).append(component));
        }
    }

    private static String visible(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }
}
