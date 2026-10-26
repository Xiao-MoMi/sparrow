package net.momirealms.sparrow.feature.warp.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.feature.warp.WarpFeature;
import net.momirealms.sparrow.feature.warp.WarpTeleportMessage;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class WarpCommand extends BukkitCommandFeature {
    private final WarpFeature feature;

    public WarpCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin, @NotNull WarpFeature feature) {
        super(manager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> named = builder.required(
                "name",
                TokenParser.tokenParser(),
                SuggestionProvider.blockingStrings((context, input) -> this.feature.suggest(context.sender(), input.peekString()))
        );
        manager.command(named.handler(this::execute));
        manager.command(named.required("player", GlobalPlayerParser.clusterPlayerParser())
                .permission(this.otherPermission(builder))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String name = context.get("name");
        Warp warp = this.feature.registry().get(name);
        if (warp == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(name));
            return;
        }
        if (!this.feature.visible(context.sender(), warp)) {
            this.handleFeedback(context, MessageConstants.COMMAND_WARP_NO_PERMISSION, Component.text(warp.name()));
            return;
        }
        String input = context.getOrDefault("player", null);
        OfflineSparrowPlayer target;
        if (input != null) {
            target = this.resolveOnlinePlayer(context, input);
            if (target == null) return;
        } else if (context.sender() instanceof Player player) {
            target = this.plugin().playerManager().getOnlinePlayer(player);
            if (target == null) {
                this.handleFeedback(context, MessageConstants.TELEPORT_UNAVAILABLE);
                return;
            }
        } else {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        UUID requester = context.sender() instanceof Player player ? player.getUniqueId() : null;
        WarpTeleportMessage message = new WarpTeleportMessage(target.uniqueId(), target.name(), warp.id(), warp.name(), requester);
        if (target instanceof BukkitSparrowPlayer) {
            message.execute();
        } else {
            String server = target.getCurrentServerId();
            if (server == null) {
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_OFFLINE, Component.text(target.name()));
                return;
            }
            this.plugin().messageBrokerManager().publishOneWay(message, server);
        }
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "warp";
    }
}