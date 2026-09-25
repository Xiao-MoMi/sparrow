package net.momirealms.sparrow.plugin.command.parser;

import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.text.format.TextColor;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.minecraft.extras.parser.TextColorParser;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class OptionalTextColorParser<C> implements ArgumentParser<C, Optional<TextColor>>, BlockingSuggestionProvider.Strings<C> {
    private final TextColorParser<C> delegate = new TextColorParser<>();

    @NotNull
    public static <C> ParserDescriptor<C, Optional<TextColor>> optionalTextColorParser() {
        return ParserDescriptor.of(new OptionalTextColorParser<>(), new TypeToken<Optional<TextColor>>() {});
    }

    @Override
    @NotNull
    public ArgumentParseResult<Optional<TextColor>> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        // 省略颜色时保留 flag 输入, 交给后续的 Cloud flag 解析器处理.
        if (input.peekString().startsWith("-")) return ArgumentParseResult.success(Optional.empty());
        return this.delegate.parse(context, input).mapSuccess(Optional::of);
    }

    @Override
    @NotNull
    public Iterable<String> stringSuggestions(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return this.delegate.stringSuggestions(context, input);
    }
}
