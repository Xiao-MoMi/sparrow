package net.momirealms.sparrow.plugin.command.parser;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.server.ServerStatus;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ServerWorldParser<C> implements ArgumentParser<C, String>, BlockingSuggestionProvider<C> {
    private final Function<CommandContext<C>, List<ServerStatus>> servers;
    private final ArgumentParser<C, String> names = StringParser.<C>quotedStringParser().parser();

    private ServerWorldParser(@NotNull Function<CommandContext<C>, List<ServerStatus>> servers) {
        this.servers = servers;
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> byServerId(@NotNull String serverId) {
        return byServerId(() -> serverId);
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> byServerId(@NotNull Supplier<String> serverId) {
        return byServerId(context -> serverId.get());
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> byServerId(@NotNull Function<CommandContext<C>, String> serverId) {
        Function<CommandContext<C>, List<ServerStatus>> servers = context -> {
            ServerStatus status = SparrowPlugin.instance().serverManager().getOnlineServer(serverId.apply(context));
            return status == null ? List.of() : List.of(status);
        };
        return ParserDescriptor.of(new ServerWorldParser<>(servers), String.class);
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> byTags(@NotNull Set<String> tags) {
        Set<String> snapshot = Set.copyOf(tags);
        return byTags(() -> snapshot);
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> byTags(@NotNull Supplier<Set<String>> tags) {
        return byTags(context -> tags.get());
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> byTags(@NotNull Function<CommandContext<C>, Set<String>> tags) {
        Function<CommandContext<C>, List<ServerStatus>> servers = context -> SparrowPlugin.instance().serverManager().getOnlineServersByTags(tags.apply(context));
        return ParserDescriptor.of(new ServerWorldParser<>(servers), String.class);
    }

    @NotNull
    @Override
    public ArgumentParseResult<String> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        ArgumentParseResult<String> parsed = this.names.parse(context, input);
        return parsed.flatMapSuccess(name -> {
            List<ServerStatus> servers = this.servers.apply(context);
            if (servers.isEmpty()) {
                return ArgumentParseResult.failure(new ParseException(context, "argument.parse.failure.server-offline"));
            }
            for (int serverIndex = 0, serverCount = servers.size(); serverIndex < serverCount; serverIndex++) {
                List<String> worlds = servers.get(serverIndex).worlds();
                for (int worldIndex = 0, worldCount = worlds.size(); worldIndex < worldCount; worldIndex++) {
                    String world = worlds.get(worldIndex);
                    if (world.equalsIgnoreCase(name)) return ArgumentParseResult.success(world);
                }
            }
            return ArgumentParseResult.failure(new ParseException(context, "argument.parse.failure.server-world", CaptionVariable.of("input", name)));
        });
    }

    @NotNull
    @Override
    public List<Suggestion> suggestions(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String value = input.remainingInput();
        boolean quoted = value.startsWith("\"");
        String prefix = quoted ? value.substring(1) : value;
        return this.servers.apply(context).stream()
                .flatMap(status -> status.worlds().stream())
                .filter(world -> world.regionMatches(true, 0, prefix, 0, prefix.length()))
                .distinct()
                .sorted()
                .map(world -> {
                    String suggestion = quoted || world.indexOf(' ') >= 0 ? "\"" + world.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : world;
                    return Suggestion.suggestion(suggestion);
                })
                .toList();
    }

    private static final class ParseException extends ParserException {

        private ParseException(@NotNull CommandContext<?> context, @NotNull String caption, @NotNull CaptionVariable... variables) {
            super(ServerWorldParser.class, context, Caption.of(caption), variables);
        }
    }
}