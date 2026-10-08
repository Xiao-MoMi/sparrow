package net.momirealms.sparrow.feature.spawn.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.spawn.Spawn;
import net.momirealms.sparrow.feature.spawn.SpawnFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class SetSpawnCommand extends BukkitCommandFeature {
    private final SpawnFeature feature;

    public SetSpawnCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull SpawnFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        Command.Builder<Player> cmd = builder.senderType(Player.class)
                .handler(context -> {
                    Spawn spawn = new Spawn(ServerConfig.serverId(), WorldLocation.from(context.sender().getLocation()));
                    this.feature.service()
                            .set(spawn)
                            .thenAccept(
                                    ignored -> this.handleFeedback(
                                            context,
                                            MessageConstants.COMMAND_SET_SPAWN_SUCCESS,
                                            Component.text(spawn.server())
                                    )
                            )
                            .exceptionally(error -> {
                                this.plugin().logger().warn("Failed to set spawn", error);
                                this.handleFeedback(context, MessageConstants.COMMAND_SPAWN_STORAGE_FAILED);
                                return null;
                            });
                });
        manager.command(cmd);
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "set-spawn";
    }
}