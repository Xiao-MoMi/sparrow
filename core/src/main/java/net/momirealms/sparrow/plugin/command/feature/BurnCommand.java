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
import net.momirealms.sparrow.plugin.command.parser.TickDurationParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class BurnCommand extends BukkitCommandFeature {

    public BurnCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> command = builder.required("time", TickDurationParser.tickDurationParser());
        manager.command(command.required("targets", EntityTargetParser.entitiesParser())
                .permission(this.otherPermission(command))
                .handler(this::execute));
        manager.command(command.handler(this::execute));
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
        int ticks = context.get("time");
        BatchCommandFeedback feedback = new BatchCommandFeedback(this, context, targets.size());
        for (EntityTarget target : targets) {
            Entity entity;
            switch (target) {
                case EntityTarget.Local local -> entity = local.entity();
                case EntityTarget.Player reference -> {
                    OfflineSparrowPlayer player = this.resolveOnlinePlayer(context, reference.nameOrUuid(), feedback);
                    if (player == null) continue;
                    if (!(player instanceof BukkitSparrowPlayer local)) {
                        this.sendRemoteRequest(context, player, new PlayerStateMessage(player.uniqueId(), PlayerStateMessage.Action.BURN, ticks), feedback);
                        continue;
                    }
                    entity = local.platformPlayer;
                }
            }
            this.runForTarget(feedback, entity, () -> {
                entity.setFireTicks(ticks);
                feedback.success(
                        (entity == context.sender() ? MessageConstants.COMMAND_BURN_SUCCESS_SELF : MessageConstants.COMMAND_BURN_SUCCESS),
                        Component.text(entity.getName()),
                        Component.text(ticks)
                );
            });
        }
    }

    @Override
    public String getFeatureID() {
        return "burn";
    }
}