package net.momirealms.sparrow.plugin.command.parser;

import org.bukkit.Location;
import org.incendo.cloud.bukkit.parser.location.LocationParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.jetbrains.annotations.NotNull;

public final class LocationFlagParser<C> implements ArgumentParser<C, Location>, BlockingSuggestionProvider.Strings<C> {
    private final LocationParser<C> delegate = new LocationParser<>();

    @NotNull
    public static <C> ParserDescriptor<C, Location> locationFlagParser() {
        return ParserDescriptor.of(new LocationFlagParser<>(), Location.class);
    }

    @Override
    @NotNull
    public ArgumentParseResult<Location> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        ArgumentParseResult<Location> result = this.delegate.parse(context, input);
        if (result.parsedValue().isPresent()) {
            // Cloud 用参数后的空格识别 flag 已结束, 原版坐标解析器会把这些空格一起消耗.
            int cursor = input.cursor();
            while (cursor > 0 && input.input().charAt(cursor - 1) == ' ') {
                cursor--;
            }
            input.cursor(cursor);
        }
        return result;
    }

    @Override
    @NotNull
    public Iterable<String> stringSuggestions(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return this.delegate.stringSuggestions(context, input);
    }
}
