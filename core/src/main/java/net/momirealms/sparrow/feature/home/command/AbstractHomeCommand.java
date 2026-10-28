package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.feature.home.HomeService;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

abstract class AbstractHomeCommand extends BukkitCommandFeature {
    protected final HomeFeature feature = SparrowPlugin.instance().featureManager().feature(HomeFeature.ID, HomeFeature.class);

    protected AbstractHomeCommand(CommandManager manager, SparrowPlugin plugin) {
        super(manager, plugin);
    }

    protected CompletableFuture<List<Suggestion>> suggest(CommandContext<CommandSender> context, CommandInput input) {
        String token = input.peekString();
        int separator = token.indexOf('.');
        if (separator == 0) return CompletableFuture.completedFuture(List.of());
        String ownerInput = separator < 0 ? null : token.substring(0, separator);
        String prefix = Home.key(token.substring(separator + 1));
        String qualifier = separator < 0 ? "" : token.substring(0, separator + 1);
        List<Suggestion> owners = new ArrayList<>();
        if (separator < 0 && context.sender().hasPermission(this.commandConfig().getPermission() + ".other")) {
            for (Suggestion player : this.plugin().playerManager().registry.suggestGlobalPlayers(token)) {
                owners.add(Suggestion.suggestion(player.suggestion() + "."));
            }
        }
        return this.lookupOwner(context.sender(), ownerInput).thenCompose(owner -> {
            if (owner.isEmpty() || !this.canAccess(context.sender(), owner.get())) return CompletableFuture.completedFuture(owners);
            return this.feature.service().list(owner.get().uniqueId()).thenApply(homes -> homes.stream()
                    .filter(home -> home.getNameKey().startsWith(prefix) && (separator >= 0 || !home.name().contains(".")))
                    .map(home -> Suggestion.suggestion(qualifier + home.name()))
                    .collect(() -> new ArrayList<>(owners), List::add, List::addAll));
        });
    }

    protected CompletableFuture<Void> withTarget(CommandContext<? extends CommandSender> context, BiFunction<SparrowPlayer, String, CompletableFuture<Void>> action) {
        String input = context.get("name");
        int separator = input.indexOf('.');
        if (separator == 0 || separator == input.length() - 1) {
            this.handleFeedback(context, MessageConstants.COMMAND_HOME_INVALID_TARGET, Component.text(input));
            return CompletableFuture.completedFuture(null);
        }
        String owner = separator < 0 ? null : input.substring(0, separator);
        String name = input.substring(separator + 1);
        return this.withOwner(context, owner, found -> action.apply(found, name));
    }

    protected CompletableFuture<Void> withOwner(CommandContext<? extends CommandSender> context, @Nullable String input, Function<SparrowPlayer, CompletableFuture<Void>> action) {
        return this.lookupOwner(context.sender(), input).thenCompose(found -> {
            if (found.isEmpty()) {
                if (input == null) {
                    this.handleFeedback(context, MessageConstants.COMMAND_HOME_OWNER_REQUIRED);
                } else {
                    this.handleFeedback(context, MessageConstants.COMMAND_HOME_UNKNOWN_OWNER, Component.text(input));
                }
                return CompletableFuture.completedFuture(null);
            }
            return this.useOwner(context, found.get(), action);
        });
    }

    protected CompletableFuture<Optional<SparrowPlayer>> lookupOwner(CommandSender sender, @Nullable String input) {
        SparrowPlayer self = sender instanceof Player player ? this.plugin().playerManager().getOnlinePlayer(player) : null;
        if (input == null) return CompletableFuture.completedFuture(Optional.ofNullable(self));
        UUID uuid = UUIDUtils.parse(input);
        if (self != null && (self.uniqueId().equals(uuid) || self.name().equalsIgnoreCase(input))) {
            return CompletableFuture.completedFuture(Optional.of(self));
        }
        return uuid == null ? this.plugin().playerManager().getPlayer(input) : this.plugin().playerManager().getPlayer(uuid);
    }

    protected CompletableFuture<Void> useOwner(CommandContext<? extends CommandSender> context, SparrowPlayer owner, Function<SparrowPlayer, CompletableFuture<Void>> action) {
        if (!this.canAccess(context.sender(), owner)) {
            this.handleFeedback(context, MessageConstants.COMMAND_HOME_NO_PERMISSION);
            return CompletableFuture.completedFuture(null);
        }
        return action.apply(owner);
    }

    private boolean canAccess(CommandSender sender, SparrowPlayer owner) {
        return (sender instanceof Player player && player.getUniqueId().equals(owner.uniqueId())) || sender.hasPermission(this.commandConfig().getPermission() + ".other");
    }

    protected CompletableFuture<Void> withHome(CommandContext<? extends CommandSender> context, BiFunction<SparrowPlayer, Home, CompletableFuture<Void>> action) {
        return this.withTarget(context, (owner, name) -> this.feature.service().find(owner.uniqueId(), name).thenCompose(home -> {
            if (home.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_HOME_UNKNOWN, Component.text(name), Component.text(owner.name()));
                return CompletableFuture.completedFuture(null);
            }
            return action.apply(owner, home.get());
        }));
    }

    protected void saved(CommandContext<?> context, SparrowPlayer owner, HomeService.Result result, String name, TranslatableComponent success, Component... arguments) {
        switch (result.status()) {
            case CREATED, UPDATED, DELETED -> this.handleFeedback(context, success, arguments);
            case DUPLICATE_NAME -> this.handleFeedback(context, MessageConstants.COMMAND_HOME_EXISTS, Component.text(name), Component.text(owner.name()));
            case NOT_FOUND -> {
                String target = context.get("name");
                this.handleFeedback(context, MessageConstants.COMMAND_HOME_UNKNOWN, Component.text(target.substring(target.indexOf('.') + 1)), Component.text(owner.name()));
            }
            case INVALID_NAME -> this.handleFeedback(context, MessageConstants.COMMAND_HOME_INVALID_NAME, Component.text(name), Component.text(Home.MAX_NAME_LENGTH));
            case LIMIT_REACHED -> this.handleFeedback(context, MessageConstants.COMMAND_HOME_LIMIT_REACHED);
        }
    }
}