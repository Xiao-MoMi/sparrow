package net.momirealms.sparrow.feature.spawn.command;

import net.momirealms.sparrow.feature.spawn.SpawnFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class DelSpawnCommand extends BukkitCommandFeature {
    private final SpawnFeature feature;

    public DelSpawnCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull SpawnFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        this.feature.service().delete()
                .thenAccept(deleted -> {
                    this.handleFeedback(context, deleted ? MessageConstants.COMMAND_DEL_SPAWN_SUCCESS : MessageConstants.COMMAND_SPAWN_NOT_SET);
                })
                .exceptionally(error -> {
                    this.plugin().logger().warn("Failed to delete spawn", error);
                    this.handleFeedback(context, MessageConstants.COMMAND_SPAWN_STORAGE_FAILED);
                    return null;
                });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "del-spawn";
    }
}