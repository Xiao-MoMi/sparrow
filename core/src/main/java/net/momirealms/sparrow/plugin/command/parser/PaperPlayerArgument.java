package net.momirealms.sparrow.plugin.command.parser;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PaperPlayerArgument<N> implements CustomArgumentType<String, N> {
    private final ArgumentType<N> nativeType;
    private final @Nullable EntityArgument selector;

    PaperPlayerArgument(@Nullable EntityArgument selector, ArgumentType<N> nativeType) {
        this.nativeType = nativeType;
        this.selector = selector;
    }

    @NotNull
    @Override
    public String parse(@NotNull StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        if (reader.canRead() && reader.peek() == '@') {
            if (this.selector == null) throw EntityArgument.ERROR_SELECTORS_NOT_ALLOWED.createWithContext(reader);
            this.selector.parse(reader, true, true);
        } else {
            while (reader.canRead() && reader.peek() != ' ') {
                reader.skip();
            }
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    @NotNull
    @Override
    public ArgumentType<N> getNativeType() {
        return this.nativeType;
    }
}