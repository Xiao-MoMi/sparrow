package net.momirealms.sparrow.plugin.command.parser;

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
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeParser<C> implements ArgumentParser.FutureArgumentParser<C, Integer>, SuggestionProvider<C> {
    private static final Pattern PART = Pattern.compile("([0-9]+)([dhmst]?)");

    @NotNull
    public static <C> ParserDescriptor<C, Integer> timeParser() {
        return ParserDescriptor.of(new TimeParser<>(), Integer.class);
    }

    @Override
    @NotNull
    public CompletableFuture<ArgumentParseResult<Integer>> parseFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String value = input.readString();
        Matcher matcher = PART.matcher(value);
        long ticks = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end || (matcher.group(2).isEmpty() && (end != 0 || matcher.end() != value.length()))) {
                return ArgumentParseResult.failureFuture(new TimeParseException(value, context));
            }
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException exception) {
                return ArgumentParseResult.failureFuture(new TimeParseException(value, context));
            }
            int multiplier = switch (matcher.group(2)) {
                case "d" -> 1728000;
                case "h" -> 72000;
                case "m" -> 1200;
                case "s" -> 20;
                default -> 1;
            };
            if (amount > (Integer.MAX_VALUE - ticks) / multiplier) {
                return ArgumentParseResult.failureFuture(new TimeParseException(value, context));
            }
            ticks += amount * multiplier;
            end = matcher.end();
        }
        if (end == 0 || end != value.length()) {
            return ArgumentParseResult.failureFuture(new TimeParseException(value, context));
        }
        return ArgumentParseResult.successFuture((int) ticks);
    }

    @Override
    @NotNull
    public CompletableFuture<List<Suggestion>> suggestionsFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return CompletableFuture.completedFuture(List.of("5s", "10s", "30s", "1m", "1m30s").stream().map(Suggestion::suggestion).toList());
    }

    public static final class TimeParseException extends ParserException {
        public TimeParseException(String input, CommandContext<?> context) {
            super(TimeParser.class, context, Caption.of("argument.parse.failure.time"), CaptionVariable.of("input", input));
        }
    }
}
