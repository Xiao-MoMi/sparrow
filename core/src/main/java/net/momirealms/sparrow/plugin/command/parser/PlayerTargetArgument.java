package net.momirealms.sparrow.plugin.command.parser;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

final class PlayerTargetArgument extends EntityArgument {
    private static final Pattern NAME_OR_UUID = Pattern.compile("[A-Za-z0-9_]{1,16}|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final List<String> SINGLE_SELECTORS = List.of("@s", "@p");
    private static final List<String> MULTIPLE_SELECTORS = List.of("@s", "@p", "@a");

    private final List<String> selectors;

    PlayerTargetArgument(boolean single) {
        super(single, false); // 原版将 UUID 作为实体标识解析
        this.selectors = single ? SINGLE_SELECTORS : MULTIPLE_SELECTORS;
    }

    @NotNull
    @Override
    public EntitySelector parse(@NotNull StringReader reader) throws CommandSyntaxException {
        // 原版在单目标校验失败时把游标归零, 使用参数局部游标与整条命令游标衔接.
        int start = reader.getCursor();
        StringReader argument = new StringReader(reader.getRemaining());
        try {
            if (argument.canRead() && argument.peek() == '@') {
                if (argument.canRead(2)) {
                    String selector = argument.getString().substring(0, 2);
                    if (!this.selectors.contains(selector)) {
                        throw EntitySelectorParser.ERROR_UNKNOWN_SELECTOR_TYPE.createWithContext(argument, selector);
                    }
                }
            } else {
                String value = argument.getString();
                int end = value.indexOf(' ');
                if (end >= 0) {
                    value = value.substring(0, end);
                }
                if (!NAME_OR_UUID.matcher(value).matches()) {
                    throw EntitySelectorParser.ERROR_INVALID_NAME_OR_UUID.createWithContext(argument);
                }
            }
            return super.parse(argument, true, true);
        } finally {
            reader.setCursor(start + argument.getCursor());
        }
    }

    @NotNull
    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(@NotNull CommandContext<S> context, @NotNull SuggestionsBuilder builder) {
        String value = builder.getRemaining();
        if (value.isEmpty() || value.equals("@")) {
            return SharedSuggestionProvider.suggest(this.selectors, builder);
        }
        if (value.length() < 2 || !this.selectors.contains(value.substring(0, 2))) return Suggestions.empty();
        StringReader reader = new StringReader(builder.getInput());
        reader.setCursor(builder.getStart());
        // 使用命令自身的权限, 选择器解析与补全共用同一权限语义.
        EntitySelectorParser parser = new EntitySelectorParser(reader, true);
        try {
            parser.parse(true);
        } catch (CommandSyntaxException ignored) {
            // 输入尚未完整时, 原版解析器保留当前可补全的位置.
        }
        return parser.fillSuggestions(builder, offset -> {});
    }
}