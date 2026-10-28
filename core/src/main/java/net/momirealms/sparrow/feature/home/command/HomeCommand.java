package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class HomeCommand extends AbstractHomeCommand {

    public HomeCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin, @NotNull HomeFeature feature) {
        super(manager, plugin, feature);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.optional("name", TokenParser.tokenParser(), this::suggest)
                .senderType(Player.class)
                .futureHandler(this::execute));
    }

    private CompletableFuture<Void> execute(CommandContext<Player> context) {
        BukkitSparrowPlayer player = this.plugin().playerManager().getOnlinePlayer(context.sender());
        if (context.contains("name")) return this.withHome(context, (owner, home) -> this.teleport(player, home));
        return this.withOwner(context, null, owner -> this.feature.service().list(owner.uniqueId()).thenCompose(homes -> {
            String defaultKey = Home.key(this.feature.getDefaultName());
            for (int i = 0, size = homes.size(); i < size; i++) {
                Home home = homes.get(i);
                if (home.getNameKey().equals(defaultKey)) return this.teleport(player, home);
            }
            if (homes.size() == 1) return this.teleport(player, homes.getFirst());
            if (homes.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_HOME_EMPTY, Component.text(owner.name()));
            } else {
                HomePanels.list(context.sender(), owner, homes, 1).send(context.sender());
            }
            return CompletableFuture.completedFuture(null);
        }));
    }

    private CompletableFuture<Void> teleport(BukkitSparrowPlayer player, Home home) {
        return this.feature.teleport(player, home).thenAccept(reason -> player.sendTranslatedMessage(
                reason == null ? MessageConstants.COMMAND_HOME_SUCCESS.arguments(Component.text(home.name())) : reason
        ));
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "home";
    }
}