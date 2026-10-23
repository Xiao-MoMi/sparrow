package net.momirealms.sparrow.plugin.command.feature;

import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.ClearChatMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

public final class ClearChatCommand extends BukkitCommandFeature {

    public ClearChatCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.optional("player", GlobalPlayerParser.clusterPlayerParser())
                .handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        String input = context.getOrDefault("player", null);
        if (input == null) {
            for (BukkitSparrowPlayer player : this.plugin().playerManager().getOnlinePlayers()) {
                new ClearChatMessage(player.uniqueId()).execute();
            }
            return;
        }
        OfflineSparrowPlayer target = this.resolveOnlinePlayer(context, input);
        if (target == null) return;
        ClearChatMessage message = new ClearChatMessage(target.uniqueId());
        if (target instanceof BukkitSparrowPlayer) {
            message.execute();
        } else {
            this.sendRemoteRequest(context, target, message);
        }
    }

    @Override
    public String getFeatureID() {
        return "clear_chat";
    }
}