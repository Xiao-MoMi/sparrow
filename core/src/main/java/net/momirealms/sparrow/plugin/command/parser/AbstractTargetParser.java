package net.momirealms.sparrow.plugin.command.parser;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import org.incendo.cloud.brigadier.parser.WrappedBrigadierParser;
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

abstract class AbstractTargetParser<C, T> implements ArgumentParser<C, T>, SuggestionProvider<C> {
    private static final int GLOBAL_SUGGESTION_THRESHOLD = 200;

    private final PlayerManager playerManager = SparrowPlugin.instance().playerManager();
    private final TargetArgument argument;
    private final WrappedBrigadierParser<C, EntitySelector> selector;
    private final boolean single;

    AbstractTargetParser(boolean single, boolean playersOnly) {
        this.argument = new TargetArgument(single, playersOnly);
        this.selector = new WrappedBrigadierParser<>(this.argument);
        this.single = single;
    }

    @NotNull
    public final ArgumentType<?> getNativeArgumentType() {
        return new PaperTargetArgument<>(this.argument, this.single ? ArgumentTypes.entity() : ArgumentTypes.entities());
    }

    @NotNull
    @Override
    public final ArgumentParseResult<T> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String value = input.peekString();
        ArgumentParseResult<EntitySelector> parsed = this.selector.parse(context, input);
        if (parsed.failure().isPresent()) return ArgumentParseResult.failure(parsed.failure().get());
        try {
            return ArgumentParseResult.success(this.resolve(context, value, parsed.parsedValue().get()));
        } catch (CommandSyntaxException exception) {
            return ArgumentParseResult.failure(exception);
        }
    }

    @NotNull
    protected abstract T resolve(@NotNull CommandContext<C> context, @NotNull String input, @NotNull EntitySelector selector) throws CommandSyntaxException;

    @NotNull
    @Override
    public final CompletableFuture<? extends Iterable<? extends Suggestion>> suggestionsFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        String prefix = input.peekString();
        if (prefix.startsWith("@")) return this.selector.suggestionsFuture(context, input);
        if (!prefix.isEmpty()) return CompletableFuture.completedFuture(this.playerManager.registry.suggestGlobalPlayers(prefix));
        return this.selector.suggestionsFuture(context, input).thenApply(selectors -> {
            List<Suggestion> players = this.playerManager.getGlobalOnlinePlayerCount() <= GLOBAL_SUGGESTION_THRESHOLD
                    ? this.playerManager.registry.suggestGlobalPlayers(prefix)
                    : this.playerManager.registry.suggestServerPlayers(ServerConfig.serverId(), prefix);
            List<Suggestion> suggestions = new ArrayList<>(players);
            for (Suggestion suggestion : selectors) {
                suggestions.add(suggestion);
            }
            return suggestions;
        });
    }
}