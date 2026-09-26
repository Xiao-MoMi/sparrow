package net.momirealms.sparrow.locale.tag;

import net.kyori.adventure.text.ComponentLike;
import net.momirealms.sparrow.message.Context;
import net.momirealms.sparrow.message.ParsingException;
import net.momirealms.sparrow.message.tag.Tag;
import net.momirealms.sparrow.message.tag.resolver.ArgumentQueue;
import net.momirealms.sparrow.message.tag.resolver.StaticTagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class IndexedArgumentTag extends StaticTagResolver {
    private final List<? extends ComponentLike> args;

    public IndexedArgumentTag(@NotNull List<? extends ComponentLike> args) {
        super("arg");
        this.args = args;
    }

    @Override
    @NotNull
    public Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx) throws ParsingException {
        final int index = arguments.popOr("No argument number provided").asInt().orElseThrow(() -> ctx.newException("Invalid argument number", arguments));
        if (index < 0 || index >= this.args.size()) {
            throw ctx.newException("Invalid argument number", arguments);
        }
        return Tag.selfClosingInserting(this.args.get(index));
    }
}