package net.momirealms.sparrow.plugin.command.panel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.Components;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommandPanelTest {
    private final CommandManager manager = mock(CommandManager.class);
    private final CommandSender sender = mock(CommandSender.class);

    @Test
    void lineJoinsPartsAndActionsProvideSpacesWithoutLeadingOrTrailingNewlines() {
        CommandPanel panel = new CommandPanel(this.manager, this.sender);
        panel.line(Component.text("first"), Component.text(" line"));
        panel.actions(Component.text("one"), Component.text("two"));
        panel.line(Component.text("last"));
        assertEquals("first line\none two\nlast", Components.toPlain(panel.build()));
    }

    @Test
    void builtComponentsKeepTheirContentWhenMoreLinesAreAdded() {
        CommandPanel panel = new CommandPanel(this.manager, this.sender).line(Component.text("first"));
        Component first = panel.build();
        panel.line(Component.text("second"));
        assertEquals("first", Components.toPlain(first));
        assertEquals("first\nsecond", Components.toPlain(panel.build()));
    }

    @Test
    void headerAndEmptyStateUseSharedTemplatesAndPageMetadata() {
        Component title = Component.translatable("feature.title");
        TextPage<String> page = new TextPage<>(1, 10, 23, List.of("entry"));
        CommandPanel panel = new CommandPanel(this.manager, this.sender).header(title, page).empty();
        Component expected = Component.empty()
                .append(Components.translatable("command.panel.header", title, Component.text(2), Component.text(3), Component.text(23L)))
                .append(Component.newline()).append(Components.translatable("command.panel.empty"));
        assertEquals(expected, panel.build());
    }

    @Test
    void navigationKeepsFiltersAndDisablesLinksAtPageBoundaries() {
        Player player = mock(Player.class);
        CommandFeature feature = mock(CommandFeature.class);
        when(this.manager.feature("history")).thenReturn(feature);
        when(feature.commandConfig()).thenReturn(new CommandConfig(true, List.of("/staff history"), "staff.history"));
        when(player.hasPermission("staff.history")).thenReturn(true);
        String suffix = " --operator Steve --active";
        List<Integer> requestedPages = new ArrayList<>();
        TextPage<String> first = new TextPage<>(0, 10, 23, List.of("entry"));
        CommandPanel panel = new CommandPanel(this.manager, player).navigation("history", first, index -> {
            requestedPages.add(index);
            return "Alex --page " + index + suffix;
        });
        assertEquals(List.of(2, 1), requestedPages);
        TranslatableComponent navigation = this.navigation(panel);
        Component previous = (Component) navigation.arguments().getFirst().value();
        Component next = (Component) navigation.arguments().get(3).value();
        Component refresh = (Component) navigation.arguments().get(4).value();
        assertNull(previous.clickEvent());
        assertEquals(Components.translatable("command.panel.first_page"), previous.hoverEvent().value());
        assertEquals(ClickEvent.runCommand("/staff history Alex --page 2" + suffix), next.clickEvent());
        assertEquals(ClickEvent.runCommand("/staff history Alex --page 1" + suffix), refresh.clickEvent());

        TextPage<String> last = new TextPage<>(2, 10, 23, List.of("entry"));
        requestedPages.clear();
        navigation = this.navigation(new CommandPanel(this.manager, player).navigation("history", last, index -> {
            requestedPages.add(index);
            return "Alex --page " + index + suffix;
        }));
        assertEquals(List.of(2, 3), requestedPages);
        previous = (Component) navigation.arguments().getFirst().value();
        next = (Component) navigation.arguments().get(3).value();
        assertEquals(ClickEvent.runCommand("/staff history Alex --page 2" + suffix), previous.clickEvent());
        assertNull(next.clickEvent());
        assertEquals(Components.translatable("command.panel.last_page"), next.hoverEvent().value());
    }

    @Test
    void sendUsesTheExistingFeedbackRendererWithTheBuiltComponent() {
        CommandPanel panel = new CommandPanel(this.manager, this.sender).line(Component.text("panel"));
        ArgumentCaptor<TranslatableComponent.Builder> message = ArgumentCaptor.forClass(TranslatableComponent.Builder.class);
        panel.send();
        verify(this.manager).handleCommandFeedback(same(this.sender), message.capture(), eq(panel.build()));
        assertEquals(CommandPanel.MESSAGE_KEY, ((TranslatableComponent) message.getValue().asComponent()).key());
    }

    private TranslatableComponent navigation(CommandPanel panel) {
        return assertInstanceOf(TranslatableComponent.class, panel.build().children().getFirst());
    }
}
