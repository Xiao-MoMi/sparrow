package net.momirealms.sparrow.locale.tag;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.message.Context;
import net.momirealms.sparrow.message.ParsingException;
import net.momirealms.sparrow.message.tag.Tag;
import net.momirealms.sparrow.message.tag.resolver.ArgumentQueue;
import net.momirealms.sparrow.message.tag.resolver.StaticTagResolver;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code <arg:key[:default]>}, 从 {@link MessageContext} 中读取命名参数, 参数缺失时使用默认值.
 */
public final class NamedArgumentTag extends StaticTagResolver {
    public static final NamedArgumentTag INSTANCE = new NamedArgumentTag();

    private NamedArgumentTag() {
        super("arg");
    }

    @Override
    @Nullable
    public Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx) throws ParsingException {
        if (!(ctx.target() instanceof MessageContext context)) return null;
        String key = arguments.popOr("No argument key provided").value();
        Object argument = context.argument(key);
        if (argument == null) {
            argument = arguments.popOr("No default value provided").value();
        }
        if (argument instanceof Component component) {
            return Tag.selfClosingInserting(component);
        } else if (argument instanceof ItemStack itemStack) {
            return Tag.selfClosingInserting(itemStack.effectiveName().hoverEvent(itemStack));
        } else {
            return Tag.selfClosingInserting(ctx.deserialize(argument.toString()));
        }
    }
}