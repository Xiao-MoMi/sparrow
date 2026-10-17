package net.momirealms.sparrow.plugin.command.parser;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.server.ServerStatus;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ServerParser<C> implements ArgumentParser<C, String>, BlockingSuggestionProvider<C> {
    private final Function<CommandContext<C>, List<ServerStatus>> servers;

    private ServerParser(@NotNull Function<CommandContext<C>, List<ServerStatus>> servers) {
        this.servers = servers;
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> all() {
        return of(context -> SparrowPlugin.instance().serverManager().getOnlineServers());
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> allExceptCurrent() {
        return of(context -> {
            String current = ServerConfig.serverId();
            return SparrowPlugin.instance().serverManager().getOnlineServers().stream()
                    .filter(status -> !status.serverId().equals(current))
                    .toList();
        });
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> byTag(@NotNull String tag) {
        return byTags(Set.of(tag));
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> byTag(@NotNull Supplier<String> tag) {
        return byTags(() -> Set.of(tag.get()));
    }

    @NotNull
    public static <C> ParserDescriptor<C, String> byTag(@NotNull Function<CommandContext<C>, String> tag) {
        return byTags(context -> Set.of(tag.apply(context)));
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
        return of(context -> SparrowPlugin.instance().serverManager().getOnlineServersByTags(tags.apply(context)));
    }

    @NotNull
    private static <C> ParserDescriptor<C, String> of(@NotNull Function<CommandContext<C>, List<ServerStatus>> servers) {
        return ParserDescriptor.of(new ServerParser<>(servers), String.class);
    }

    @NotNull
    @Override
    public ArgumentParseResult<String> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        ArgumentParseResult<String> parsed = ArgumentParseResult.success(input.readString());
        return parsed.flatMapSuccess(name -> {
            List<ServerStatus> servers = this.servers.apply(context);
            if (servers.isEmpty()) {
                return ArgumentParseResult.failure(new ParseException(context, "argument.parse.failure.server-offline"));
            }
            for (int i = 0, size = servers.size(); i < size; i++) {
                String server = servers.get(i).serverId();
                if (server.equalsIgnoreCase(name)) return ArgumentParseResult.success(server);
            }
            return ArgumentParseResult.failure(new ParseException(context, "argument.parse.failure.server-unknown", CaptionVariable.of("input", name)));
        });
    }

    @NotNull
    @Override
    public List<Suggestion> suggestions(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String prefix = input.peekString();
        return this.servers.apply(context).stream()
                .map(ServerStatus::serverId)
                .filter(server -> server.regionMatches(true, 0, prefix, 0, prefix.length()))
                .map(Suggestion::suggestion)
                .toList();
    }

    private static final class ParseException extends ParserException {

        private ParseException(@NotNull CommandContext<?> context, @NotNull String caption, @NotNull CaptionVariable... variables) {
            super(ServerParser.class, context, Caption.of(caption), variables);
        }
    }
}