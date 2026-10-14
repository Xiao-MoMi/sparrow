package net.momirealms.sparrow.plugin.command.parser;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.redis.message.WorldListRequest;
import net.momirealms.sparrow.redis.message.WorldListResponse;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ServerWorldParser<C> implements ArgumentParser.FutureArgumentParser<C, String>, SuggestionProvider<C> {
    private final String serverArgument;
    private final ArgumentParser<C, String> names = StringParser.<C>quotedStringParser().parser();

    public ServerWorldParser(@NotNull String serverArgument) {
        this.serverArgument = serverArgument;
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> serverWorldParser(@NotNull String serverArgument) {
        return ParserDescriptor.of(new ServerWorldParser<>(serverArgument), String.class);
    }

    @NotNull
    @Override
    public CompletableFuture<ArgumentParseResult<String>> parseFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return this.names.parseFuture(context, input)
                .thenCompose(parsed -> parsed.flatMapSuccessFuture(name -> {
                    String server = context.get(this.serverArgument);
                    if (!this.available(server)) {
                        return ArgumentParseResult.failureFuture(new ParseException(context, "argument.parse.failure.server-offline", CaptionVariable.of("server", server)));
                    }
                    return this.worlds(server).thenApply(worlds -> {
                        for (String world : worlds) {
                            if (world.equalsIgnoreCase(name)) return ArgumentParseResult.success(world);
                        }
                        return ArgumentParseResult.failure(new ParseException(
                                context,
                                "argument.parse.failure.server-world",
                                CaptionVariable.of("input", name),
                                CaptionVariable.of("server", server)
                        ));
                    });
                }));
    }

    @NotNull
    @Override
    public CompletableFuture<? extends Iterable<? extends Suggestion>> suggestionsFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String server = context.get(this.serverArgument);
        if (!this.available(server)) return CompletableFuture.completedFuture(List.of());
        String value = input.remainingInput();
        boolean quoted = value.startsWith("\"");
        String prefix = quoted ? value.substring(1) : value;
        return this.worlds(server)
                .thenApply(worlds -> worlds.stream()
                        .filter(world -> world.regionMatches(true, 0, prefix, 0, prefix.length()))
                        .map(world -> Suggestion.suggestion(quoted || world.indexOf(' ') >= 0 ? "\"" + world.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : world))
                        .toList());
    }

    private boolean available(String server) {
        return server.equals(ServerConfig.serverId()) || SparrowPlugin.instance().serverDirectory().isOnline(server);
    }

    private CompletableFuture<List<String>> worlds(String server) {
        if (server.equals(ServerConfig.serverId())) {
            return CompletableFuture.completedFuture(Bukkit.getWorlds().stream().map(World::getName).toList());
        }
        return SparrowPlugin.instance().messageBrokerManager().broker()
                .publishTwoWay(new WorldListRequest(), server)
                .thenApply(WorldListResponse::worlds);
    }

    private static final class ParseException extends ParserException {

        private ParseException(CommandContext<?> context, String caption, CaptionVariable... variables) {
            super(ServerWorldParser.class, context, Caption.of(caption), variables);
        }
    }
}