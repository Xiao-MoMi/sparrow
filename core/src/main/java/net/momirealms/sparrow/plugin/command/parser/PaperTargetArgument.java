package net.momirealms.sparrow.plugin.command.parser;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.jetbrains.annotations.NotNull;

final class PaperTargetArgument<N> implements CustomArgumentType<String, N> {
    private final ArgumentType<N> nativeType;
    private final TargetArgument target;

    PaperTargetArgument(@NotNull TargetArgument target, @NotNull ArgumentType<N> nativeType) {
        this.nativeType = nativeType;
        this.target = target;
    }

    @NotNull
    @Override
    public String parse(@NotNull StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        this.target.parse(reader);
        return reader.getString().substring(start, reader.getCursor());
    }

    @NotNull
    @Override
    public ArgumentType<N> getNativeType() {
        return this.nativeType;
    }
}