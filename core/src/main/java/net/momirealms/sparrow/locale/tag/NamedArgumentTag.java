package net.momirealms.sparrow.locale.tag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.momirealms.sparrow.util.Components;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class NamedArgumentTag implements TagResolver {
    private final Map<String, Object> arguments;

    public NamedArgumentTag(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    @Override
    public @Nullable Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx) throws ParsingException {
        if (!has(name)) {
            return null;
        }
        String key = arguments.popOr("No argument key provided").toString();
        Object argument = this.arguments.get(key);
        if (argument == null) {
            argument = arguments.popOr("No default value provided").toString();
        }
        if (argument instanceof Component component) {
            return Tag.selfClosingInserting(component);
        } else if (argument instanceof ItemStack itemStack) {
            return Tag.selfClosingInserting(itemStack.effectiveName().hoverEvent(itemStack));
        } else {
            return Tag.selfClosingInserting(Components.miniMessage(argument.toString()));
        }
    }

    @Override
    public boolean has(@NotNull String name) {
        return name.equals("arg");
    }
}