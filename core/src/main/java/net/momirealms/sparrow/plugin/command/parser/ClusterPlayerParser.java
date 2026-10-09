package net.momirealms.sparrow.plugin.command.parser;

import net.momirealms.sparrow.player.PlayerLookup;
import net.momirealms.sparrow.cluster.PlayerDirectory;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * 按集群在线名单补全玩家名, 解析时原样接受任意名字, 离线玩家由命令通过 {@link PlayerLookup#resolvePlayer(String)} 查找.
 */
public final class ClusterPlayerParser<C> implements ArgumentParser.FutureArgumentParser<C, String>, SuggestionProvider<C> {
    private final PlayerDirectory cluster;

    public ClusterPlayerParser(@NotNull PlayerDirectory cluster) {
        this.cluster = cluster;
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> clusterPlayerParser(@NotNull PlayerDirectory cluster) {
        return ParserDescriptor.of(new ClusterPlayerParser<>(cluster), String.class);
    }

    @Override
    @NotNull
    public CompletableFuture<ArgumentParseResult<String>> parseFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return ArgumentParseResult.successFuture(input.readString());
    }

    @Override
    @NotNull
    public CompletableFuture<? extends Iterable<? extends Suggestion>> suggestionsFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return CompletableFuture.completedFuture(this.cluster.suggest(input.peekString()));
    }
}