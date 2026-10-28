package net.momirealms.sparrow.feature.spawn.command;

import net.momirealms.sparrow.feature.spawn.SpawnFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.FeaturePermission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.permission.AndPermission;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.permission.PermissionResult;
import org.incendo.cloud.permission.PredicatePermission;
import org.jetbrains.annotations.NotNull;

public final class SpawnCommand extends BukkitCommandFeature {

    public SpawnCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        SpawnFeature feature = this.plugin().featureManager().feature(SpawnFeature.ID, SpawnFeature.class);
        builder = builder.permission(Permission.allOf(builder.commandPermission(), new ConfiguredPermission(feature)));
        manager.command(builder.senderType(Player.class).handler(context -> {
            Player player = context.sender();
            this.plugin().scheduler().platform().run(
                    () -> this.plugin().featureManager().feature(SpawnFeature.ID, SpawnFeature.class).teleport(this.plugin().playerManager().getOnlinePlayer(player)),
                    () -> {},
                    player
            );
        }));
    }

    @Override
    public void registerRelatedFunctions() {
        this.commandManager().getCommandManager().exceptionController().registerHandler(NoPermissionException.class, context -> {
            Permission permission = context.exception().permissionResult().permission();
            CommandSender sender = context.context().sender();
            if (FeaturePermission.findDisabled(permission) != null || !sender.hasPermission(this.commandConfig().getPermission()) || !ConfiguredPermission.isMissing(permission)) {
                throw context.exception();
            }
            this.handleFeedback(sender, MessageConstants.COMMAND_SPAWN_NOT_SET);
        });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return SpawnFeature.ID;
    }

    private record ConfiguredPermission(SpawnFeature feature) implements PredicatePermission<CommandSender> {

        @NotNull
        @Override
        public PermissionResult testPermission(@NotNull CommandSender sender) {
            return PermissionResult.of(this.feature.hasDestination(), this);
        }

        private static boolean isMissing(Permission permission) {
            if (permission instanceof ConfiguredPermission configured) return !configured.feature.hasDestination();
            if (!(permission instanceof AndPermission)) return false;
            for (Permission child : permission.permissions()) {
                if (isMissing(child)) return true;
            }
            return false;
        }
    }
}