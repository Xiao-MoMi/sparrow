package net.momirealms.sparrow.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComponentsTest {
    @Test
    void constructsTranslationWithoutRenderingItsKeyOrNestedArgument() {
        Component argument = Component.translatable("nested.key");
        TranslatableComponent component = Components.translatable("outer.key", argument);
        assertEquals("outer.key", component.key());
        assertEquals(argument, component.arguments().getFirst().value());
        assertTrue(component.children().isEmpty());
    }

    @Test
    void sharedBuilderKeepsItsArgumentsStyleAndChildrenAcrossCalls() {
        TranslatableComponent.Builder key = Component.translatable().key("message.key").color(NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand("/action")).append(Component.text("suffix")).arguments(Component.text("original"));
        Component original = key.asComponent();
        TranslatableComponent first = Components.translatable(key, Component.text("first"));
        TranslatableComponent second = Components.translatable(key, Component.text("second"));
        assertEquals(Component.text("first"), first.arguments().getFirst().value());
        assertEquals(Component.text("second"), second.arguments().getFirst().value());
        assertEquals(NamedTextColor.AQUA, first.color());
        assertEquals(ClickEvent.runCommand("/action"), first.clickEvent());
        assertEquals(original.children(), first.children());
        assertEquals(original, key.asComponent());
    }
}
