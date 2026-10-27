package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.feature.home.HomeFilter;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DelAllHomeCommand extends BukkitCommandFeature {
    private final HomeFeature feature;

    public DelAllHomeCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin, @NotNull HomeFeature feature) {
        super(manager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder
                .flag(manager.flagBuilder("player").withComponent(CommandComponent.<CommandSender, String>builder("player", TokenParser.tokenParser())
                        .suggestionProvider((context, input) -> CompletableFuture.completedFuture(this.plugin().playerManager().registry.suggestGlobalPlayers(input.peekString())))))
                .flag(manager.flagBuilder("server").withComponent(StringParser.quotedStringParser()))
                .flag(manager.flagBuilder("world").withComponent(StringParser.quotedStringParser()))
                .senderType(ConsoleCommandSender.class)
                .futureHandler(this::execute));
    }

    private CompletableFuture<Void> execute(CommandContext<ConsoleCommandSender> context) {
        String player = context.flags().getValue("player", null);
        String server = context.flags().getValue("server", null);
        String world = context.flags().getValue("world", null);
        if (player == null && server == null && world == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_DEL_ALL_HOMES_FILTER_REQUIRED);
            return CompletableFuture.completedFuture(null);
        }
        if (player == null) return this.delete(context, new HomeFilter(null, server, world));
        UUID uuid = UUIDUtils.parse(player);
        CompletableFuture<Optional<UUID>> resolving = uuid == null
                ? this.plugin().playerManager().getPlayer(player).thenApply(found -> found.map(SparrowPlayer::uniqueId))
                : CompletableFuture.completedFuture(Optional.of(uuid));
        return resolving.thenCompose(owner -> {
            if (owner.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_HOME_UNKNOWN_OWNER, Component.text(player));
                return CompletableFuture.completedFuture(null);
            }
            return this.delete(context, new HomeFilter(owner.get(), server, world));
        });
    }

    private CompletableFuture<Void> delete(CommandContext<ConsoleCommandSender> context, HomeFilter filter) {
        return this.feature.service().deleteAll(filter)
                .thenAccept(count -> this.handleFeedback(context, MessageConstants.COMMAND_DEL_ALL_HOMES_SUCCESS, Component.text(count)));
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "del_all_homes";
    }
}