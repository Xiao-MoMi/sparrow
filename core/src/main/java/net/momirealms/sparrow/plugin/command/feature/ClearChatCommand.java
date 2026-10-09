package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class ClearChatCommand extends BukkitCommandFeature {
    private static final int CLEAR_LINES = 100;
    private static final Component BLANK_LINE = Component.text(" ");

    public ClearChatCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.optional("player", PlayerParser.playerParser()).handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        Player target = context.getOrDefault("player", null);
        Collection<? extends Player> players = target == null ? Bukkit.getOnlinePlayers() : List.of(target);
        for (Player player : players) {
            SparrowPlayer receiver = this.plugin().playerManager().getPlayer(player);
            for (int i = 0; i < CLEAR_LINES; i++) {
                receiver.sendMessage(BLANK_LINE);
            }
        }
    }

    @Override
    public String getFeatureID() {
        return "clear-chat";
    }
}