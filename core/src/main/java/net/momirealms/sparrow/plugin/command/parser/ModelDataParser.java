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

import java.util.regex.Pattern;

public final class ModelDataParser<C> implements ArgumentParser<C, Number> {
    private static final Pattern NUMBER = Pattern.compile("[+-]?[0-9]+(?:\\.[0-9]+)?");

    @NotNull
    public static <C> ParserDescriptor<C, Number> modelDataParser() {
        return ParserDescriptor.of(new ModelDataParser<>(), Number.class);
    }

    @Override
    @NotNull
    public ArgumentParseResult<Number> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String value = input.readString();
        if (NUMBER.matcher(value).matches()) {
            try {
                if (value.indexOf('.') < 0) return ArgumentParseResult.success(Integer.valueOf(value));
                float number = Float.parseFloat(value);
                if (Float.isFinite(number)) return ArgumentParseResult.success(number);
            } catch (NumberFormatException ignored) {
                return ArgumentParseResult.failure(new ModelDataParseException(value, context));
            }
        }
        return ArgumentParseResult.failure(new ModelDataParseException(value, context));
    }

    public static final class ModelDataParseException extends ParserException {
        public ModelDataParseException(String input, CommandContext<?> context) {
            super(ModelDataParser.class, context, Caption.of("argument.parse.failure.modeldata"), CaptionVariable.of("input", input));
        }
    }
}
