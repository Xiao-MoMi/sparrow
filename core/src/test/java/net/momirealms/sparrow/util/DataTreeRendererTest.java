package net.momirealms.sparrow.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataTreeRendererTest {
    @Test
    void displaysMarkupLiterallyAndCopiesOriginalText() {
        String value = "<click:run_command:'/op test'>quoted ' text</click>\nnext\\line";
        List<Component> lines = DataTreeRenderer.render(Map.of("<red>key", value), true);
        assertEquals(List.of("<red>key: " + value.replace("\n", "\\n")), this.plain(lines));
        assertEquals(List.of(ClickEvent.copyToClipboard(value)), this.clicks(lines));
    }

    @Test
    void preservesNestedMapsListsAndEmptyContainers() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", "minecraft:stone");
        entry.put("data", Map.of("values", List.of(List.of(1, 2), Map.of(), List.of())));
        List<Component> lines = DataTreeRenderer.render(Map.of("items", List.of(entry)), true);
        assertEquals(List.of("items:", "  - id: minecraft:stone", "    data:", "      values:", "        -", "          - 1", "          - 2", "        - {}", "        - []"), this.plain(lines));
        assertEquals(List.of("minecraft:stone", "1", "2", "{}", "[]"), this.clicks(lines).stream().map(ClickEvent::value).toList());
    }

    @Test
    void displaysPrimitiveArraysAsValuesRatherThanObjectIdentities() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bytes", new byte[]{1, -2});
        data.put("ints", new int[]{3, 4});
        data.put("longs", new long[]{5, Long.MAX_VALUE});
        List<Component> lines = DataTreeRenderer.render(data, true);
        assertEquals(List.of("bytes: [1, -2]", "ints: [3, 4]", "longs: [5, 9223372036854775807]"), this.plain(lines));
        assertEquals(3, this.clicks(lines).size());
    }

    @Test
    void hoverTreeHasTheSameTextWithoutCopyActions() {
        Map<String, Object> data = Map.of("components", Map.of("minecraft:lore", List.of("one", "two")));
        List<Component> chat = DataTreeRenderer.render(data, true);
        List<Component> hover = DataTreeRenderer.render(data, false);
        assertEquals(this.plain(chat), this.plain(hover));
        assertTrue(this.clicks(hover).isEmpty());
    }

    private List<String> plain(List<Component> lines) {
        return lines.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
    }

    private List<ClickEvent> clicks(List<Component> components) {
        List<ClickEvent> events = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            Component component = components.get(i);
            if (component.clickEvent() != null) {
                events.add(component.clickEvent());
            }
            events.addAll(this.clicks(component.children()));
        }
        return events;
    }
}
