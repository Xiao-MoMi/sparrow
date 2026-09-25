package net.momirealms.sparrow.plugin.command.parser;

import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public final class URLParser<C> implements ArgumentParser.FutureArgumentParser<C, URL> {
    @NotNull
    public static <C> ParserDescriptor<C, URL> urlParser() {
        return ParserDescriptor.of(new URLParser<>(), URL.class);
    }

    @Override
    @NotNull
    public CompletableFuture<ArgumentParseResult<URL>> parseFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String value = input.readString();
        try {
            return ArgumentParseResult.successFuture(URI.create(value).toURL());
        } catch (IllegalArgumentException | MalformedURLException exception) {
            return ArgumentParseResult.failureFuture(new ParseException(context, value));
        }
    }

    private static final class ParseException extends ParserException {
        private ParseException(CommandContext<?> context, String value) {
            super(URLParser.class, context, Caption.of("argument.parse.failure.url"), CaptionVariable.of("input", value));
        }
    }
}