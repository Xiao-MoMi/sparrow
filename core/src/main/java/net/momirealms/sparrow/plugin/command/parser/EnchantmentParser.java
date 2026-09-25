package net.momirealms.sparrow.plugin.command.parser;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
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

public final class EnchantmentParser<C> implements ArgumentParser.FutureArgumentParser<C, Enchantment>, SuggestionProvider<C> {

    @NotNull
    public static <C> ParserDescriptor<C, Enchantment> enchantmentParser() {
        return ParserDescriptor.of(new EnchantmentParser<>(), Enchantment.class);
    }

    @Override
    @NotNull
    public CompletableFuture<ArgumentParseResult<Enchantment>> parseFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String value = input.readString();
        NamespacedKey key = NamespacedKey.fromString(value);
        Enchantment enchantment = key == null ? null : Registry.ENCHANTMENT.get(key);
        if (enchantment == null) {
            return ArgumentParseResult.failureFuture(new ParseException(context, value));
        }
        return ArgumentParseResult.successFuture(enchantment);
    }

    @Override
    @NotNull
    public CompletableFuture<List<Suggestion>> suggestionsFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return CompletableFuture.completedFuture(Registry.ENCHANTMENT.stream().map(enchantment -> enchantment.getKey().asString()).sorted().map(Suggestion::suggestion).toList());
    }

    private static final class ParseException extends ParserException {
        private ParseException(CommandContext<?> context, String value) {
            super(EnchantmentParser.class, context, Caption.of("argument.parse.failure.enchantment"), CaptionVariable.of("input", value));
        }
    }
}