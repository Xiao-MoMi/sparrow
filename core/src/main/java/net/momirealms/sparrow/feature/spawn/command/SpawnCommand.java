package net.momirealms.sparrow.feature.spawn.command;

import net.momirealms.sparrow.feature.spawn.SpawnFeature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.jetbrains.annotations.NotNull;

public final class SpawnCommand extends BukkitCommandFeature {

    public SpawnCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class).handler(context -> {
            Player player = context.sender();
            this.plugin().scheduler().platform().run(
                    () -> this.plugin().featureManager().feature(SpawnFeature.ID, SpawnFeature.class).teleport(this.plugin().playerManager().getOnlinePlayer(player)),
                    () -> {},
                    player
            );
        }));
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return SpawnFeature.ID;
    }
}