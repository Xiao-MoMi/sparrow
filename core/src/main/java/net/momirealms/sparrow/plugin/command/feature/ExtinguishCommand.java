package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.PlayerStateMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BatchCommandFeedback;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.EntityTarget;
import net.momirealms.sparrow.plugin.command.parser.EntityTargetParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class ExtinguishCommand extends BukkitCommandFeature {

    public ExtinguishCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("targets", EntityTargetParser.entitiesParser())
                .permission(this.otherPermission(builder))
                .handler(this::execute));
        manager.command(builder.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        List<EntityTarget> targets = context.getOrDefault("targets", null);
        if (targets == null) {
            if (!(context.sender() instanceof Player player)) {
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
                return;
            }
            targets = List.of(new EntityTarget.Player(player.getUniqueId().toString()));
        }
        BatchCommandFeedback feedback = new BatchCommandFeedback(this, context, targets.size());
        for (EntityTarget target : targets) {
            Entity entity;
            switch (target) {
                case EntityTarget.Local local -> entity = local.entity();
                case EntityTarget.Player reference -> {
                    OfflineSparrowPlayer player = this.resolveOnlinePlayer(context, reference.nameOrUuid(), feedback);
                    if (player == null) continue;
                    if (!(player instanceof BukkitSparrowPlayer local)) {
                        this.sendRemoteRequest(context, player, new PlayerStateMessage(player.uniqueId(), PlayerStateMessage.Action.EXTINGUISH, 0), feedback);
                        continue;
                    }
                    entity = local.platformPlayer;
                }
            }
            this.runForTarget(feedback, entity, () -> {
                entity.setFireTicks(0);
                feedback.success(
                        (entity == context.sender() ? MessageConstants.COMMAND_EXTINGUISH_SUCCESS_SELF : MessageConstants.COMMAND_EXTINGUISH_SUCCESS),
                        Component.text(entity.getName())
                );
            });
        }
    }

    @Override
    public String getFeatureID() {
        return "extinguish";
    }
}