package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class HomeListCommand extends AbstractHomeCommand {

    public HomeListCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin, @NotNull HomeFeature feature) {
        super(manager, plugin, feature);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.optional("owner_or_page", TokenParser.tokenParser(), this::suggestOwnerOrPage)
                .optional("page", IntegerParser.integerParser(1))
                .futureHandler(this::execute));
    }

    private CompletableFuture<List<Suggestion>> suggestOwnerOrPage(CommandContext<CommandSender> context, CommandInput input) {
        String prefix = input.peekString();
        List<Suggestion> suggestions = new ArrayList<>();
        if (context.sender().hasPermission(this.commandConfig().getPermission() + ".other")) {
            suggestions.addAll(this.plugin().playerManager().registry.suggestGlobalPlayers(prefix));
        }
        if ("1".startsWith(prefix)) {
            suggestions.add(Suggestion.suggestion("1"));
        }
        return CompletableFuture.completedFuture(suggestions);
    }

    private CompletableFuture<Void> execute(CommandContext<CommandSender> context) {
        String input = context.getOrDefault("owner_or_page", null);
        if (input == null) return this.withOwner(context, null, owner -> this.show(context, owner, 1));
        return this.lookupOwner(context.sender(), input).thenCompose(found -> {
            if (found.isPresent()) {
                return this.useOwner(context, found.get(), owner -> this.show(context, owner, context.getOrDefault("page", 1)));
            }
            if (!context.contains("page")) {
                Optional<Integer> page = IntegerParser.<CommandSender>integerParser(1).parser().parse(context, CommandInput.of(input)).parsedValue();
                if (page.isPresent()) return this.withOwner(context, null, owner -> this.show(context, owner, page.get()));
            }
            this.handleFeedback(context, MessageConstants.COMMAND_HOME_UNKNOWN_OWNER, Component.text(input));
            return CompletableFuture.completedFuture(null);
        });
    }

    private CompletableFuture<Void> show(CommandContext<CommandSender> context, SparrowPlayer owner, int page) {
        return this.feature.service().list(owner.uniqueId()).thenAccept(homes -> HomePanels.list(context.sender(), owner, homes, page));
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "home_list";
    }
}