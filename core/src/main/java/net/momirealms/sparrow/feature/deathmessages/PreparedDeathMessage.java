package net.momirealms.sparrow.feature.deathmessages;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.player.message.MessageTemplate;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

record PreparedDeathMessage(@NotNull MessageTemplate template) {

    PreparedDeathMessage(@NotNull String message, @NotNull String format, @NotNull Map<String, Component> arguments) {
        this(new MessageTemplate(message, format, arguments));
    }

    @NotNull
    Component render(@NotNull Player receiver) {
        return this.template.render(receiver);
    }
}