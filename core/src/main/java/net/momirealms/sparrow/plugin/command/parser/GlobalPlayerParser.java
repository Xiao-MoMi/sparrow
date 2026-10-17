package net.momirealms.sparrow.plugin.command.parser;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.leangen.geantyref.TypeToken;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.incendo.cloud.brigadier.parser.WrappedBrigadierParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class GlobalPlayerParser<C, T> implements ArgumentParser<C, T>, SuggestionProvider<C> {
    private static final int GLOBAL_SUGGESTION_THRESHOLD = 200;

    private final PlayerManager playerManager = SparrowPlugin.instance().playerManager();
    private final WrappedBrigadierParser<C, EntitySelector> selector;
    private final Function<List<String>, T> result;
    private final boolean single;

    private GlobalPlayerParser(boolean single, @NotNull Function<List<String>, T> result) {
        this.selector = new WrappedBrigadierParser<>(new PlayerTargetArgument(single));
        this.result = result;
        this.single = single;
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> clusterPlayerParser() {
        return ParserDescriptor.of(new GlobalPlayerParser<>(true, List::getFirst), String.class);
    }

    @NotNull
    public static <C> ParserDescriptor<C, List<String>> clusterPlayersParser() {
        return ParserDescriptor.of(new GlobalPlayerParser<>(false, Function.identity()), new TypeToken<List<String>>() {});
    }

    @NotNull
    public ArgumentType<?> getNativeArgumentType() {
        // 实体参数支持 UUID, 单目标标记用于客户端检查选择器数量.
        return new PaperPlayerArgument<>(new PlayerTargetArgument(this.single), this.single ? ArgumentTypes.entity() : ArgumentTypes.entities());
    }

    @NotNull
    @Override
    public ArgumentParseResult<T> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String value = input.peekString();
        ArgumentParseResult<EntitySelector> parsed = this.selector.parse(context, input);
        if (parsed.failure().isPresent()) {
            return ArgumentParseResult.failure(parsed.failure().get());
        }
        if (!value.startsWith("@")) {
            return ArgumentParseResult.success(this.result.apply(List.of(value)));
        }
        CommandSourceStack source = context.get(WrappedBrigadierParser.COMMAND_CONTEXT_BRIGADIER_NATIVE_SENDER);
        try {
            List<ServerPlayer> players = parsed.parsedValue().get().findPlayers(source);
            if (players.isEmpty()) {
                return ArgumentParseResult.failure(EntityArgument.NO_PLAYERS_FOUND.create());
            }
            return ArgumentParseResult.success(this.result.apply(players.stream().map(player -> player.getBukkitEntity().getName()).toList()));
        } catch (CommandSyntaxException exception) {
            return ArgumentParseResult.failure(exception);
        }
    }

    @NotNull
    @Override
    public CompletableFuture<? extends Iterable<? extends Suggestion>> suggestionsFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String prefix = input.peekString();
        if (prefix.startsWith("@")) {
            return this.selector.suggestionsFuture(context, input);
        }
        if (!prefix.isEmpty()) {
            return CompletableFuture.completedFuture(this.playerManager.playerCache.suggestGlobalPlayers(prefix));
        }
        return this.selector.suggestionsFuture(context, input).thenApply(selectors -> {
            List<Suggestion> suggestions = new ArrayList<>();
            if (this.playerManager.getGlobalOnlinePlayers().size() <= GLOBAL_SUGGESTION_THRESHOLD) {
                suggestions.addAll(this.playerManager.playerCache.suggestGlobalPlayers(prefix));
            } else {
                for (BukkitSparrowPlayer player : this.playerManager.getOnlinePlayers()) {
                    suggestions.add(Suggestion.suggestion(player.name()));
                }
                suggestions.sort(Comparator.comparing(Suggestion::suggestion, String.CASE_INSENSITIVE_ORDER));
            }
            for (Suggestion suggestion : selectors) {
                suggestions.add(suggestion);
            }
            return suggestions;
        });
    }
}