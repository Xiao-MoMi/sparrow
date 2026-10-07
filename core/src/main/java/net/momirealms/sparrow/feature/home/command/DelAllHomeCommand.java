package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.HomeStore;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.PlayerRef;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class DelAllHomeCommand extends AbstractHomeCommand {
    public DelAllHomeCommand(HomeFeature feature) {
        super(feature);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(ConsoleCommandSender.class)
                .flag(manager.flagBuilder("player").withComponent(ClusterPlayerParser.clusterPlayerParser(this.plugin().playerManager().cluster())).build())
                .flag(manager.flagBuilder("server").withComponent(StringParser.quotedStringParser()).build())
                .flag(manager.flagBuilder("world").withComponent(StringParser.quotedStringParser()).build()).handler(this::execute));
    }

    private void execute(CommandContext<ConsoleCommandSender> context) {
        String player = context.flags().getValue("player", null);
        String server = context.flags().getValue("server", null);
        String world = context.flags().getValue("world", null);
        if (player == null && server == null && world == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_DEL_ALL_HOME_FILTER_REQUIRED);
            return;
        }
        CompletableFuture<Optional<PlayerRef>> resolving = player == null ? CompletableFuture.completedFuture(Optional.empty()) : this.plugin().playerManager().resolvePlayer(player);
        resolving.thenCompose(owner -> {
            if (player != null && owner.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(player));
                return CompletableFuture.completedFuture(null);
            }
            HomeStore.Filter filter = new HomeStore.Filter(owner.map(PlayerRef::uuid).orElse(null), server, world);
            return this.feature.service().deleteAll(filter).thenAccept(count -> this.handleFeedback(context, MessageConstants.COMMAND_DEL_ALL_HOME_SUCCESS, Component.text(count)));
        }).exceptionally(error -> { this.failed(context.sender(), error); return null; });
    }

    @Override
    public String getFeatureID() {
        return "del-all-home";
    }
}