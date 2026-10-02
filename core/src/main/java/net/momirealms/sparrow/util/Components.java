package net.momirealms.sparrow.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.momirealms.sparrow.locale.tag.MessageContext;
import net.momirealms.sparrow.message.tag.resolver.TagResolver;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class Components {
    private Components() {}

    @NotNull
    public static TranslatableComponent translatable(@NotNull String key, @NotNull ComponentLike... arguments) {
        return Component.translatable(key).arguments(arguments);
    }

    @NotNull
    public static TranslatableComponent translatable(@NotNull TranslatableComponent.Builder key, @NotNull ComponentLike... arguments) {
        return ((TranslatableComponent) key.asComponent()).arguments(arguments);
    }

    public static String toPlain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String toMiniMessage(Component component) {
        return AdventureHelper.miniMessage().serialize(component);
    }

    public static Component miniMessage(String miniMessage) {
        return AdventureHelper.miniMessage().deserialize(miniMessage);
    }

    public static Component miniMessage(String text, boolean legacy) {
        return miniMessage(legacy ? AdventureHelper.legacyToMiniMessage(text) : text);
    }

    public static Component miniMessage(String miniMessage, TagResolver... resolvers) {
        return AdventureHelper.miniMessage().deserialize(miniMessage, resolvers);
    }

    public static Component miniMessage(String miniMessage, Map<String, Object> arguments) {
        return AdventureHelper.miniMessage().deserialize(miniMessage, MessageContext.of(arguments));
    }

    public static Component miniMessage(String miniMessage, MessageContext context, TagResolver... resolvers) {
        return AdventureHelper.miniMessage().deserialize(miniMessage, context, resolvers);
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
