package net.momirealms.sparrow.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.Components;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SparrowPlayerRenderTest {
    private final TranslationManager translations = mock(TranslationManager.class);
    private final SparrowPlayer player = mock(SparrowPlayer.class, CALLS_REAL_METHODS);
    private MockedStatic<SparrowPlugin> pluginInstance;

    @BeforeEach
    void setUp() {
        SparrowPlugin plugin = mock(SparrowPlugin.class);
        when(plugin.translationManager()).thenReturn(this.translations);
        when(this.player.locale()).thenReturn(Locale.SIMPLIFIED_CHINESE);
        this.pluginInstance = mockStatic(SparrowPlugin.class);
        this.pluginInstance.when(SparrowPlugin::instance).thenReturn(plugin);
    }

    @AfterEach
    void tearDown() {
        this.pluginInstance.close();
    }

    @Test
    void rendersACompositeComponentUsingThePlayerLocaleWithoutSendingIt() {
        Component panel = Component.text("prefix ").append(Components.translatable("message.key"));
        Component rendered = Component.text("rendered panel");
        when(this.translations.render(panel, Locale.SIMPLIFIED_CHINESE)).thenReturn(rendered);
        assertSame(rendered, this.player.render(panel));
        verify(this.translations).render(same(panel), eq(Locale.SIMPLIFIED_CHINESE));
        verify(this.player, never()).sendMessage(any(Component.class), anyBoolean());
    }

    @Test
    void messageConstantOverloadsConstructAndRenderBeforeSending() {
        TranslatableComponent.Builder key = Component.translatable().key("message.key");
        Component argument = Component.text("argument");
        Component rendered = Component.text("rendered message");
        Component original = key.asComponent();
        when(this.translations.render(Components.translatable(key, argument), Locale.SIMPLIFIED_CHINESE)).thenReturn(rendered);
        this.player.sendMessage(key, argument);
        this.player.sendActionBar(key, argument);
        verify(this.player).sendMessage(same(rendered), eq(false));
        verify(this.player).sendMessage(same(rendered), eq(true));
        verify(this.translations, times(2)).render(Components.translatable(key, argument), Locale.SIMPLIFIED_CHINESE);
        assertEquals(original, key.asComponent());
    }

    @Test
    void sendingAComponentPassesItThroughWithoutRenderingAgain() {
        Component component = Components.translatable("message.key");
        this.player.sendMessage(component);
        this.player.sendActionBar(component);
        verify(this.player).sendMessage(same(component), eq(false));
        verify(this.player).sendMessage(same(component), eq(true));
        verifyNoInteractions(this.translations);
    }
}
