package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class SetHomeCommand extends AbstractHomeCommand {

    public SetHomeCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin) {
        super(manager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class).optional("name", TokenParser.tokenParser()).futureHandler(this::execute));
    }

    private CompletableFuture<Void> execute(CommandContext<Player> context) {
        BukkitSparrowPlayer player = this.plugin().playerManager().getOnlinePlayer(context.sender());
        String name = context.getOrDefault("name", this.feature.getDefaultName());
        WorldLocation location = WorldLocation.from(context.sender().getLocation());
        int limit = this.feature.service().getLimit(player);
        return this.feature.service().create(player.uniqueId(), name, ServerConfig.serverId(), location, limit)
                .thenAccept(result -> this.saved(context, player, result, name, MessageConstants.COMMAND_SET_HOME_CREATED, Component.text(name)));
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "set_home";
    }
}