package net.momirealms.sparrow.feature.deathmessages;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.momirealms.sparrow.locale.tag.MessageContext;
import net.momirealms.sparrow.player.message.TemplateBroadcastMessage;
import net.momirealms.sparrow.proxy.bukkit.entity.CraftPlayerProxy;
import net.momirealms.sparrow.proxy.paper.adventure.PaperAdventureProxy;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

final class DeathMessageRenderer {
    private static final Key PLAYER_TYPE = Key.key("minecraft:player");

    @Nullable
    TemplateBroadcastMessage prepare(@NotNull DeathMessageContext context, @NotNull DeathMessagesConfig config, @NotNull List<String> messages) {
        if (messages.isEmpty()) return null;
        String message = messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
        if (message.isEmpty()) return null;

        Map<String, Component> arguments = new HashMap<>();
        arguments.put("original", context.original());
        arguments.put("message_key", Component.text(context.getMessageKey()));
        arguments.put("server", Component.text(context.server()));
        arguments.put("world", Component.text(context.world()));
        arguments.put("x", Component.text(context.x()));
        arguments.put("y", Component.text(context.y()));
        arguments.put("z", Component.text(context.z()));
        Component weapon = context.getWeapon();
        if (weapon != null) arguments.put("weapon", weapon);
        Component reason = context.getDeathReason();
        if (reason != null) arguments.put("death_reason", reason);
        arguments.put("victim", this.renderPlayer(context.victim(), context.getVictimName(), config.display(), arguments));
        Component attacker = context.getAttackerName();
        if (attacker != null) {
            arguments.put("attacker", this.renderAttacker(attacker, config.display(), arguments));
        }
        return new TemplateBroadcastMessage(message, config.format(), arguments);
    }

    @NotNull
    private Component renderAttacker(@NotNull Component name, @NotNull DeathMessagesConfig.Display display, @NotNull Map<String, Component> arguments) {
        // 原版名字组件的实体悬浮携带归因对象的 UUID, 包括摔落助攻和补刀对象.
        HoverEvent<?> hover = name.hoverEvent();
        if (hover != null && hover.value() instanceof HoverEvent.ShowEntity entity && entity.type().equals(PLAYER_TYPE)) {
            return this.renderPlayer(Bukkit.getPlayer(entity.id()), name, display, arguments);
        }
        Map<String, Object> displayArguments = new HashMap<>(arguments);
        displayArguments.put("entity_name", name);
        return AdventureHelper.miniMessage().deserialize(display.mobName(), MessageContext.of(displayArguments));
    }

    @NotNull
    private Component renderPlayer(@Nullable Player player, @NotNull Component originalName, @NotNull DeathMessagesConfig.Display display, @NotNull Map<String, Component> arguments) {
        Map<String, Object> displayArguments = new HashMap<>(arguments);
        if (player != null) {
            displayArguments.put("name", Component.text(player.getName()));
            Object nativeName = PaperAdventureProxy.INSTANCE.asVanilla(CraftPlayerProxy.INSTANCE.displayName(player));
            displayArguments.put("display_name", AdventureHelper.jsonToComponent(CraftChatMessage.toJSON((net.minecraft.network.chat.Component) nativeName)));
        } else {
            HoverEvent.ShowEntity entity = (HoverEvent.ShowEntity) originalName.hoverEvent().value();
            displayArguments.put("name", entity.name());
            displayArguments.put("display_name", originalName);
        }
        MessageContext messageContext = MessageContext.of(player, displayArguments);
        Component name = AdventureHelper.miniMessage().deserialize(display.playerName(), messageContext);
        List<String> hoverLines = display.playerHover();
        if (!hoverLines.isEmpty()) {
            TextComponent.Builder hover = Component.text();
            for (int i = 0, size = hoverLines.size(); i < size; i++) {
                if (i > 0) {
                    hover.append(Component.newline());
                }
                hover.append(AdventureHelper.miniMessage().deserialize(hoverLines.get(i), messageContext));
            }
            name = name.hoverEvent(hover.build());
        }
        return name;
    }
}