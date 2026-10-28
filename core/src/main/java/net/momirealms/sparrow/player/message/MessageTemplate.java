package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.tag.MessageContext;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public record MessageTemplate(@NotNull String message, @NotNull String format, @NotNull Map<String, Component> arguments) {

    public MessageTemplate {
        arguments = Map.copyOf(arguments);
    }

    @NotNull
    public Component render(@NotNull Player receiver) {
        Map<String, Object> receiverArguments = new HashMap<>(this.arguments);
        Component sentence = AdventureHelper.miniMessage().deserialize(this.message, MessageContext.of(receiver, receiverArguments));
        Map<String, Object> formatArguments = new HashMap<>(receiverArguments);
        formatArguments.put("message", sentence);
        return AdventureHelper.miniMessage().deserialize(this.format, MessageContext.of(receiver, formatArguments));
    }
}