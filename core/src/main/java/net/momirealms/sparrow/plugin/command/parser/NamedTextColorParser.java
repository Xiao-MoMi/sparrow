package net.momirealms.sparrow.plugin.command.parser;

import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class NamedTextColorParser<C> implements ArgumentParser.FutureArgumentParser<C, NamedTextColor>, SuggestionProvider<C> {

    @NotNull
    public static <C> ParserDescriptor<C, NamedTextColor> namedTextColorParser() {
        return ParserDescriptor.of(new NamedTextColorParser<>(), NamedTextColor.class);
    }

    @Override
    @NotNull
    public CompletableFuture<ArgumentParseResult<NamedTextColor>> parseFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String value = input.readString();
        NamedTextColor color = NamedTextColor.NAMES.value(value.toLowerCase(Locale.ROOT));
        if (color == null) {
            return ArgumentParseResult.failureFuture(new ParseException(context, value));
        }
        return ArgumentParseResult.successFuture(color);
    }

    @Override
    @NotNull
    public CompletableFuture<List<Suggestion>> suggestionsFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return CompletableFuture.completedFuture(NamedTextColor.NAMES.keys().stream().map(Suggestion::suggestion).toList());
    }

    private static final class ParseException extends ParserException {
        private ParseException(CommandContext<?> context, String value) {
            super(NamedTextColorParser.class, context, Caption.of("argument.parse.failure.namedtextcolor"), CaptionVariable.of("input", value));
        }
    }
}