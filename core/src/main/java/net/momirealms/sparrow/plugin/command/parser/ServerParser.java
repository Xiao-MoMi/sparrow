package net.momirealms.sparrow.plugin.command.parser;

import net.momirealms.sparrow.cluster.ServerStatus;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class ServerParser<C> implements ArgumentParser<C, String>, SuggestionProvider<C> {
    private final Predicate<String> filter;

    public ServerParser(@NotNull Predicate<String> filter) {
        this.filter = filter;
    }

    @NotNull
    @Override
    public ArgumentParseResult<String> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return ArgumentParseResult.success(input.readString());
    }

    @NotNull
    @Override
    public CompletableFuture<? extends Iterable<? extends Suggestion>> suggestionsFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String prefix = input.peekString();
        List<Suggestion> result = new ArrayList<>();
        List<ServerStatus> servers = SparrowPlugin.instance().serverDirectory().getServers();
        for (int i = 0, size = servers.size(); i < size; i++) {
            String server = servers.get(i).serverId();
            if (!server.equals(ServerConfig.serverId()) && server.regionMatches(true, 0, prefix, 0, prefix.length()) && this.filter.test(server)) {
                result.add(Suggestion.suggestion(server));
            }
        }
        return CompletableFuture.completedFuture(result);
    }
}