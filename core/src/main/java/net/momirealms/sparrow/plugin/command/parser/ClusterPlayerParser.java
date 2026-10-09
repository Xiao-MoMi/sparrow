package net.momirealms.sparrow.plugin.command.parser;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.leangen.geantyref.TypeToken;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.momirealms.sparrow.player.PlayerLookup;
import net.momirealms.sparrow.cluster.PlayerDirectory;
import org.incendo.cloud.brigadier.parser.WrappedBrigadierParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 普通名字按集群在线名单补全, 离线玩家由命令通过 {@link PlayerLookup#resolvePlayer(String)} 查找.
 * 选择器在本服解析为玩家名, 参数声明决定是否允许选择器及其目标数量.
 */
public final class ClusterPlayerParser<C, T> implements ArgumentParser.FutureArgumentParser<C, T>, SuggestionProvider<C> {
    private final PlayerDirectory cluster;
    private final @Nullable LocalPlayerSelectorParser<C> selector;
    private final Function<List<String>, T> result;
    private final Selection selection;
    private final boolean allowOtherTokens;

    private ClusterPlayerParser(PlayerDirectory cluster, Selection selection, boolean allowOtherTokens, Function<List<String>, T> result) {
        this.cluster = cluster;
        this.selector = selection == Selection.NONE ? null : new LocalPlayerSelectorParser<>(selection == Selection.SINGLE);
        this.result = result;
        this.selection = selection;
        this.allowOtherTokens = allowOtherTokens;
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> clusterPlayerParser(@NotNull PlayerDirectory cluster) {
        return ParserDescriptor.of(new ClusterPlayerParser<>(cluster, Selection.SINGLE, false, List::getFirst), String.class);
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> clusterPlayerNameParser(@NotNull PlayerDirectory cluster) {
        return ParserDescriptor.of(new ClusterPlayerParser<>(cluster, Selection.NONE, true, List::getFirst), String.class);
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> clusterPlayerOrTokenParser(@NotNull PlayerDirectory cluster) {
        return ParserDescriptor.of(new ClusterPlayerParser<>(cluster, Selection.SINGLE, true, List::getFirst), String.class);
    }

    @NotNull
    public static <C> ParserDescriptor<C, List<String>> clusterPlayersParser(@NotNull PlayerDirectory cluster) {
        return ParserDescriptor.of(new ClusterPlayerParser<>(cluster, Selection.MULTIPLE, false, Function.identity()), new TypeToken<List<String>>() {});
    }

    @NotNull
    public ArgumentType<?> getNativeArgumentType() {
        EntityArgument selector = switch (this.selection) {
            case NONE -> null;
            case SINGLE -> EntityArgument.player();
            case MULTIPLE -> EntityArgument.players();
        };
        // 实体参数在客户端支持 UUID 和 @s; 混合参数使用允许 #ID、IP 等文本的档案参数.
        return this.allowOtherTokens
                ? new PaperPlayerArgument<>(selector, ArgumentTypes.playerProfiles())
                : new PaperPlayerArgument<>(selector, this.selection == Selection.SINGLE ? ArgumentTypes.entity() : ArgumentTypes.entities());
    }

    @Override
    @NotNull
    public CompletableFuture<ArgumentParseResult<T>> parseFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        if (!input.peekString().startsWith("@")) {
            return ArgumentParseResult.successFuture(this.result.apply(List.of(input.readString())));
        }
        if (this.selector == null) {
            return ArgumentParseResult.failureFuture(EntityArgument.ERROR_SELECTORS_NOT_ALLOWED.create());
        }
        ArgumentParseResult<EntitySelector> parsed = this.selector.parse(context, input);
        if (parsed.failure().isPresent()) {
            return ArgumentParseResult.failureFuture(parsed.failure().get());
        }
        CommandSourceStack source = context.get(WrappedBrigadierParser.COMMAND_CONTEXT_BRIGADIER_NATIVE_SENDER);
        try {
            List<ServerPlayer> players = parsed.parsedValue().get().findPlayers(source);
            if (players.isEmpty()) {
                return ArgumentParseResult.failureFuture(EntityArgument.NO_PLAYERS_FOUND.create());
            }
            return ArgumentParseResult.successFuture(this.result.apply(players.stream().map(player -> player.getBukkitEntity().getName()).toList()));
        } catch (CommandSyntaxException exception) {
            return ArgumentParseResult.failureFuture(exception);
        }
    }

    @Override
    @NotNull
    public CompletableFuture<? extends Iterable<? extends Suggestion>> suggestionsFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String prefix = input.peekString();
        if (this.selector == null) {
            return CompletableFuture.completedFuture(this.cluster.suggest(prefix));
        }
        if (prefix.startsWith("@")) {
            return this.selector.suggestionsFuture(context, input);
        }
        if (!prefix.isEmpty()) {
            return CompletableFuture.completedFuture(this.cluster.suggest(prefix));
        }
        return this.selector.suggestionsFuture(context, input).thenApply(selectors -> {
            List<Suggestion> suggestions = new ArrayList<>(this.cluster.suggest(prefix));
            for (Suggestion suggestion : selectors) {
                if (suggestion.suggestion().startsWith("@")) {
                    suggestions.add(suggestion);
                }
            }
            return suggestions;
        });
    }

    private enum Selection {
        NONE, SINGLE, MULTIPLE
    }
}