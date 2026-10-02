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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PanelButtonTest {
    private final CommandManager manager = mock(CommandManager.class);
    private final CommandFeature feature = mock(CommandFeature.class);
    private final Player player = mock(Player.class);
    private final CommandSender console = mock(CommandSender.class);
    private final Component caption = Component.translatable("feature.custom-caption");
    private CommandPanel panel;

    @BeforeEach
    void setUp() {
        when(this.manager.feature("edit")).thenReturn(this.feature);
        when(this.feature.commandConfig()).thenReturn(new CommandConfig(true, List.of("ignored", "/admin edit  ", "/other"), "staff.edit"));
        when(this.player.hasPermission(anyString())).thenReturn(true);
        when(this.console.hasPermission(anyString())).thenReturn(true);
        this.panel = new CommandPanel(this.manager, this.player);
    }

    @Test
    void runAndSuggestUseConfiguredEntryAndKeepTheInputSpace() {
        Component run = this.panel.run(this.caption, "edit", "Spawn delete").style(PanelButton.Style.DANGER).build();
        assertEquals(ClickEvent.runCommand("/admin edit Spawn delete"), run.clickEvent());
        assertEquals(Component.text("/admin edit Spawn delete"), run.hoverEvent().value());
        assertEquals("command.panel.button.danger", this.template(run).key());
        assertEquals(this.caption, this.template(run).arguments().getFirst().value());

        Component suggest = this.panel.suggest(this.caption, "edit", "Spawn rename ").build();
        assertEquals(ClickEvent.suggestCommand("/admin edit Spawn rename "), suggest.clickEvent());
        assertEquals(Components.translatable("command.panel.confirm", Component.text("/admin edit Spawn rename ")), suggest.hoverEvent().value());
    }

    @Test
    void buildRechecksBothPermissionsAfterAvailabilityWasChecked() {
        PanelButton button = this.panel.run(this.caption, "edit", "Spawn delete").permission("staff.edit.delete");
        assertTrue(button.available());
        when(this.player.hasPermission("staff.edit.delete")).thenReturn(false);
        assertFalse(button.available());
        this.assertDisabled(button.build(), "command.panel.no_permission");
        when(this.player.hasPermission("staff.edit.delete")).thenReturn(true);
        when(this.player.hasPermission("staff.edit")).thenReturn(false);
        this.assertDisabled(button.build(), "command.panel.no_permission");
        when(this.player.hasPermission("staff.edit")).thenReturn(true);
        assertEquals(ClickEvent.runCommand("/admin edit Spawn delete"), button.build().clickEvent());
    }

    @Test
    void buildUsesTheCurrentCommandConfiguration() {
        PanelButton button = this.panel.run(this.caption, "edit", "");
        assertTrue(button.available());
        when(this.feature.commandConfig()).thenReturn(new CommandConfig(true, List.of("/renamed"), "staff.edit"));
        assertEquals(ClickEvent.runCommand("/renamed"), button.build().clickEvent());
        when(this.feature.commandConfig()).thenReturn(new CommandConfig(false, List.of("/renamed"), "staff.edit"));
        this.assertDisabled(button.build(), "command.panel.unavailable");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void commandsWithoutBasePermissionRemainAvailable(String permission) {
        when(this.feature.commandConfig()).thenReturn(new CommandConfig(true, List.of("/public"), permission));
        when(this.player.hasPermission(anyString())).thenReturn(false);
        assertTrue(this.panel.run(this.caption, "edit", "").available());
        assertEquals(ClickEvent.runCommand("/public"), this.panel.run(this.caption, "edit", "").build().clickEvent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "disabled", "no-entry"})
    void unavailableCommandsDoNotProduceExecutableLinks(String state) {
        switch (state) {
            case "absent" -> when(this.manager.feature("edit")).thenReturn(null);
            case "disabled" -> when(this.feature.commandConfig()).thenReturn(new CommandConfig(false, List.of("/edit"), "staff.edit"));
            case "no-entry" -> when(this.feature.commandConfig()).thenReturn(new CommandConfig(true, List.of("edit"), "staff.edit"));
        }
        PanelButton button = this.panel.run(this.caption, "edit", "Spawn");
        assertFalse(button.available());
        this.assertDisabled(button.build(), "command.panel.unavailable");
    }

    @Test
    void businessReasonSupportsFeatureTextAndCanBeCleared() {
        Component reason = Component.text("This record has expired");
        PanelButton button = this.panel.run(this.caption, "edit", "Spawn").disabled(reason);
        assertFalse(button.available());
        Component disabled = button.build();
        assertNull(disabled.clickEvent());
        assertEquals(reason, disabled.hoverEvent().value());
        assertTrue(button.disabled(null).available());
    }

    @Test
    void consoleGetsCommandTextAndPlayerOnlyReason() {
        CommandPanel consolePanel = new CommandPanel(this.manager, this.console);
        Component action = consolePanel.suggest(this.caption, "edit", "Spawn rename ").build();
        assertNull(action.clickEvent());
        assertEquals("command.panel.console.action", this.template(action).key());
        assertEquals(Component.text("/admin edit Spawn rename "), this.template(action).arguments().get(1).value());

        PanelButton playerOnly = consolePanel.run(this.caption, "edit", "Spawn relocate").playersOnly();
        assertFalse(playerOnly.available());
        Component disabled = playerOnly.build();
        assertNull(disabled.clickEvent());
        assertEquals("command.panel.console.disabled", this.template(disabled).key());
        assertEquals(Components.translatable("command.panel.player_required"), this.template(disabled).arguments().get(1).value());
        assertTrue(this.panel.run(this.caption, "edit", "Spawn relocate").playersOnly().available());
    }

    private void assertDisabled(Component component, String reasonKey) {
        assertNull(component.clickEvent());
        assertEquals("command.panel.disabled", this.template(component).key());
        assertEquals(this.caption, this.template(component).arguments().getFirst().value());
        assertEquals(Components.translatable(reasonKey), component.hoverEvent().value());
    }

    private TranslatableComponent template(Component component) {
        return assertInstanceOf(TranslatableComponent.class, component);
    }
}
