package net.momirealms.sparrow.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.momirealms.sparrow.locale.tag.NamedArgumentTag;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class Components {
    private Components() {}

    public static String toPlain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String toMiniMessage(Component component) {
        return MiniMessage.miniMessage().serialize(component);
    }

    public static Component miniMessage(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage);
    }

    public static Component miniMessage(String text, boolean legacy) {
        return miniMessage(legacy ? AdventureHelper.legacyToMiniMessage(text) : text);
    }

    public static Component miniMessage(String miniMessage, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(miniMessage, resolvers);
    }

    public static Component miniMessage(String miniMessage, Map<String, Object> arguments) {
        return MiniMessage.miniMessage().deserialize(miniMessage, new NamedArgumentTag(arguments));
    }

    public static Component withArgs(Component component, Map<String, Object> arguments) {
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            component = component.replaceText(builder -> {
                builder.matchLiteral("<arg:" + entry.getKey() + ">");
                Object replacement = entry.getValue();
                if (replacement instanceof Component arg) {
                    builder.replacement(arg);
                } else if (replacement instanceof ItemStack itemStack) {
                    builder.replacement(itemStack.effectiveName());
                } else {
                    builder.replacement(replacement.toString());
                }
            });
        }
        return component;
    }
}
