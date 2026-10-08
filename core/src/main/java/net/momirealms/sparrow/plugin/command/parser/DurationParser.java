package net.momirealms.sparrow.plugin.command.parser;

import net.momirealms.sparrow.util.DurationUtils;
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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// 按现实时间解析的正时长, 写法见 DurationUtils.parsePositive
public final class DurationParser<C> implements ArgumentParser<C, Duration>, SuggestionProvider<C> {
    private static final List<Suggestion> SUGGESTIONS = List.of("1h", "1d", "7d", "30d", "1y").stream().map(Suggestion::suggestion).toList();

    @NotNull
    public static <C> ParserDescriptor<C, Duration> durationParser() {
        return ParserDescriptor.of(new DurationParser<>(), Duration.class);
    }

    @Override
    @NotNull
    public ArgumentParseResult<Duration> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String value = input.readString();
        try {
            return ArgumentParseResult.success(DurationUtils.parsePositive(value));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return ArgumentParseResult.failure(new DurationParseException(value, context));
        }
    }

    @Override
    @NotNull
    public CompletableFuture<List<Suggestion>> suggestionsFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return CompletableFuture.completedFuture(SUGGESTIONS);
    }

    public static final class DurationParseException extends ParserException {

        public DurationParseException(String input, CommandContext<?> context) {
            super(DurationParser.class, context, Caption.of("argument.parse.failure.duration"), CaptionVariable.of("input", input));
        }
    }
}
