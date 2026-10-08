package net.momirealms.sparrow.feature.spawn.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.spawn.Spawn;
import net.momirealms.sparrow.feature.spawn.SpawnFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.teleport.TeleportOptions;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.permission.AndPermission;
import org.incendo.cloud.permission.OrPermission;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.permission.PermissionResult;
import org.incendo.cloud.permission.PredicatePermission;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public final class SpawnCommand extends BukkitCommandFeature {
    private final SpawnFeature feature;

    public SpawnCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull SpawnFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(
            org.incendo.cloud.@NonNull CommandManager<CommandSender> manager,
            @NotNull Command.Builder<CommandSender> builder
    ) {
        manager.command(builder.senderType(Player.class)
                .permission(Permission.allOf(builder.commandPermission(), new ConfiguredPermission(this.feature)))
                .handler(this::execute));
    }

    @Override
    public void registerRelatedFunctions() {
        this.commandManager().getCommandManager().exceptionController().registerHandler(NoPermissionException.class, context -> {
            Permission permission = context.exception().permissionResult().permission();
            if (this.feature.enabled() && this.feature.spawn() == null && this.hasConfiguredPermission(permission)) {
                this.handleFeedback(context.context(), MessageConstants.COMMAND_SPAWN_NOT_SET);
            } else {
                throw context.exception();
            }
        });
    }

    private boolean hasConfiguredPermission(@NotNull Permission permission) {
        if (permission instanceof ConfiguredPermission) {
            return true;
        }
        if (!(permission instanceof AndPermission) && !(permission instanceof OrPermission)) {
            return false;
        }
        for (Permission child : permission.permissions()) {
            if (this.hasConfiguredPermission(child)) {
                return true;
            }
        }
        return false;
    }

    private void execute(@NotNull CommandContext<Player> context) {
        Spawn spawn = this.feature.spawn();
        if (spawn == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_SPAWN_NOT_SET);
            return;
        }
        Player player = context.sender();
        TeleportOptions options = this.feature.config().teleportOptions().resolve(player, true);
        this.plugin().playerManager().teleportService()
                .teleport(player, spawn.server(), spawn.location(), options)
                .thenAccept(result -> {
                    switch (result) {
                        case SUCCESS -> this.handleFeedback(context, MessageConstants.COMMAND_SPAWN_SUCCESS);
                        case CONNECTING -> this.handleFeedback(context, MessageConstants.COMMAND_SPAWN_CONNECTING, Component.text(spawn.server()));
                        case SERVER_OFFLINE -> this.handleFeedback(
                                context,
                                MessageConstants.COMMAND_SPAWN_SERVER_OFFLINE,
                                Component.text(spawn.server())
                        );
                        case INVALID -> this.handleFeedback(context, MessageConstants.COMMAND_SPAWN_INVALID);
                        case FAILED -> this.handleFeedback(context, MessageConstants.COMMAND_TELEPORT_FAILURE_SELF);
                        case COOLDOWN, CANCELLED -> {
                        }
                    }
                })
                .exceptionally(error -> {
                    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                    if (cause instanceof TimeoutException) {
                        this.handleFeedback(context, MessageConstants.COMMAND_SPAWN_TIMEOUT);
                    } else {
                        this.plugin().logger().warn("Failed to send " + player.getName() + " to spawn", error);
                        this.handleFeedback(context, MessageConstants.COMMAND_TELEPORT_FAILURE_SELF);
                    }
                    return null;
                });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return SpawnFeature.ID;
    }

    private record ConfiguredPermission(@NotNull SpawnFeature feature) implements PredicatePermission<CommandSender> {

        @NotNull
        @Override
        public PermissionResult testPermission(@NotNull CommandSender sender) {
            return PermissionResult.of(this.feature.enabled() && this.feature.spawn() != null, this);
        }

        @NotNull
        @Override
        public String permissionString() {
            return "spawn:configured";
        }
    }
}