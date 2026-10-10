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
import org.incendo.cloud.brigadier.parser.WrappedBrigadierParser;

import java.util.concurrent.CompletableFuture;

final class LocalPlayerSelectorParser<C> extends WrappedBrigadierParser<C, EntitySelector> {

    LocalPlayerSelectorParser(boolean single) {
        super(() -> new SelectorArgument(single), (argument, reader) -> ((SelectorArgument) argument).parseSelector(reader));
    }

    private static final class SelectorArgument extends EntityArgument {

        private SelectorArgument(boolean single) {
            super(single, true);
        }

        private EntitySelector parseSelector(StringReader reader) throws CommandSyntaxException {
            // 原版在单目标校验失败时把游标归零, 使用参数局部游标与 Cloud 的整条命令游标衔接.
            int start = reader.getCursor();
            StringReader argument = new StringReader(reader.getRemaining());
            try {
                return this.parse(argument, true, true);
            } finally {
                reader.setCursor(start + argument.getCursor());
            }
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            SharedSuggestionProvider source = (SharedSuggestionProvider) context.getSource();
            StringReader reader = new StringReader(builder.getInput());
            reader.setCursor(builder.getStart());
            // 使用命令自身的权限, 选择器解析与补全共用同一权限语义.
            EntitySelectorParser parser = new EntitySelectorParser(reader, true);
            try {
                parser.parse(true);
            } catch (CommandSyntaxException ignored) {
                // 输入尚未完整时, 原版解析器保留当前可补全的位置.
            }
            return parser.fillSuggestions(builder, offset -> SharedSuggestionProvider.suggest(source.getOnlinePlayerNames(), offset));
        }
    }
}