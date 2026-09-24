package net.momirealms.sparrow.plugin.command.parser;

import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.player.cluster.ClusterRoster;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

/**
 * 按集群在线名单补全玩家名, 解析时原样接受任意名字, 离线玩家由命令通过 {@link PlayerManager#resolvePlayer(String)} 查找.
 */
public final class ClusterPlayerParser<C> implements ArgumentParser<C, String>, BlockingSuggestionProvider<C> {
    private final ClusterRoster cluster;

    public ClusterPlayerParser(@NotNull ClusterRoster cluster) {
        this.cluster = cluster;
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> clusterPlayerParser(@NotNull ClusterRoster cluster) {
        return ParserDescriptor.of(new ClusterPlayerParser<>(cluster), String.class);
    }

    @Override
    @NotNull
    public ArgumentParseResult<String> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return ArgumentParseResult.success(input.readString());
    }

    @Override
    @NotNull
    public Iterable<? extends Suggestion> suggestions(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return this.cluster.suggest(input.peekString());
    }
}
