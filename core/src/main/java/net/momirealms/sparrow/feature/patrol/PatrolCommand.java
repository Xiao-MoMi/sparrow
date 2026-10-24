package net.momirealms.sparrow.feature.patrol;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

public final class PatrolCommand extends BukkitCommandFeature {

    public PatrolCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class)
                .optional("targets", GlobalPlayerParser.clusterPlayersParser())
                .handler(this::execute));
    }

    private void execute(@NotNull CommandContext<Player> context) {
        List<String> inputs = context.getOrDefault("targets", null);
        List<OfflineSparrowPlayer> candidates;
        if (inputs == null) {
            candidates = this.plugin().playerManager().getGlobalOnlinePlayers();
        } else {
            candidates = new ArrayList<>(inputs.size());
            for (int i = 0, size = inputs.size(); i < size; i++) {
                String input = inputs.get(i);
                UUID uuid = UUIDUtils.parse(input);
                OfflineSparrowPlayer target = uuid == null
                        ? this.plugin().playerManager().getGlobalOnlinePlayer(input)
                        : this.plugin().playerManager().getGlobalOnlinePlayer(uuid);
                if (target == null) {
                    this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_OFFLINE, Component.text(input));
                    return;
                }
                candidates.add(target);
            }
        }
        PatrolFeature feature = this.plugin().featureManager().feature(PatrolFeature.ID, PatrolFeature.class);
        BukkitSparrowPlayer patroller = this.plugin().playerManager().getOnlinePlayer(context.sender());
        long generation = feature.generation();
        feature.claimNext(patroller.uniqueId(), candidates).whenComplete((target, error) -> {
            if (!patroller.isOnlineLocally()) return;
            if (feature.generation() != generation) {
                this.handleFeedback(context, MessageConstants.COMMAND_PATROL_CANCELLED);
                return;
            }
            if (error != null) {
                Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                if (cause instanceof CancellationException) {
                    this.handleFeedback(context, MessageConstants.COMMAND_PATROL_CANCELLED);
                } else {
                    this.plugin().logger().warn("Failed to select a patrol target", cause);
                    this.handleFeedback(context, MessageConstants.COMMAND_PATROL_QUERY_FAILED);
                }
                return;
            }
            if (target == null) {
                this.handleFeedback(context, MessageConstants.COMMAND_PATROL_EMPTY);
                return;
            }
            this.teleport(context, patroller, target);
        });
    }

    private void teleport(@NotNull CommandContext<Player> context, @NotNull BukkitSparrowPlayer patroller, @NotNull PatrolFeature.Target target) {
        Teleport.builder()
                .server(target.server())
                .position(TeleportPosition.ofPlayer(target.uuid()))
                .commit(patroller)
                .onResult(result -> {
                    switch (result) {
                        case TeleportResult.Success ignored -> this.handleFeedback(context, MessageConstants.COMMAND_PATROL_SUCCESS, Component.text(target.name()));
                        case TeleportResult.Fail fail -> this.handleFeedback(context, MessageConstants.COMMAND_PATROL_FAILED, Component.text(target.name()), fail.reason());
                        case TeleportResult.Aborted ignored -> this.handleFeedback(context, MessageConstants.COMMAND_PATROL_FAILED, Component.text(target.name()), MessageConstants.TELEPORT_CANCELLED);
                    }
                });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return PatrolFeature.ID;
    }
}