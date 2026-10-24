package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.PlayerProfile;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.util.ServerLocation;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class TpOfflineCommand extends BukkitCommandFeature {

    public TpOfflineCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", GlobalPlayerParser.clusterPlayerParser())
                .optional("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        MultiplePlayerSelector selector = context.getOrDefault("targets", null);
        List<Player> targets;
        if (selector != null) {
            targets = List.copyOf(selector.values());
        } else if (context.sender() instanceof Player player) {
            targets = List.of(player);
        } else {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        if (targets.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        String input = context.get("player");
        UUID uuid = UUIDUtils.parse(input);
        CompletableFuture<Optional<UUID>> identity = uuid == null
                ? this.plugin().playerManager().getPlayer(input).thenApply(found -> found.map(SparrowPlayer::uniqueId))
                : CompletableFuture.completedFuture(Optional.of(uuid));
        identity.thenCompose(found -> {
            if (found.isEmpty()) return CompletableFuture.<PlayerProfile>completedFuture(null);
            return this.plugin().dataStorage().playerProfileStore().load(found.get()).thenApply(profile -> profile.orElse(null));
        }).thenAccept(profile -> {
            if (profile == null) {
                this.handleFeedback(context, MessageConstants.COMMAND_TP_OFFLINE_UNKNOWN, Component.text(input));
                return;
            }
            ServerLocation location = profile.logoutLocation();
            if (location == null) {
                this.handleFeedback(context, MessageConstants.COMMAND_TP_OFFLINE_NO_LOCATION, Component.text(profile.name()));
                return;
            }
            for (Player player : targets) {
                this.teleport(context, player, profile.name(), location);
            }
        });
    }

    private void teleport(CommandContext<CommandSender> context, Player player, String name, ServerLocation location) {
        String targetName = player.getName();
        Teleport.builder()
                .server(location.server())
                .position(location.world(), location.x(), location.y(), location.z(), location.yaw(), location.pitch())
                .commit(player)
                .onResult(result -> {
                    switch (result) {
                        case TeleportResult.Success ignored -> this.handleFeedback(
                                context,
                                player == context.sender() ? MessageConstants.COMMAND_TP_OFFLINE_SUCCESS_SELF : MessageConstants.COMMAND_TP_OFFLINE_SUCCESS,
                                Component.text(targetName),
                                Component.text(name),
                                Component.text(location.server())
                        );
                        case TeleportResult.Fail fail -> this.handleFeedback(context, MessageConstants.COMMAND_TP_OFFLINE_FAILED, Component.text(targetName), fail.reason());
                        case TeleportResult.Aborted ignored -> this.handleFeedback(context, MessageConstants.COMMAND_TP_OFFLINE_FAILED, Component.text(targetName), MessageConstants.TELEPORT_CANCELLED);
                    }
                });
    }

    @Override
    public String getFeatureID() {
        return "tp_offline";
    }
}