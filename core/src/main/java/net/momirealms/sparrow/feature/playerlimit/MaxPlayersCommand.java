package net.momirealms.sparrow.feature.playerlimit;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

public final class MaxPlayersCommand extends BukkitCommandFeature {

    public MaxPlayersCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.optional("amount", IntegerParser.integerParser(-1))
                .handler(context -> this.plugin().scheduler().platform().execute(() -> this.execute(context))));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        PlayerLimitFeature feature = this.plugin().featureManager().feature(PlayerLimitFeature.ID, PlayerLimitFeature.class);
        Integer amount = context.getOrDefault("amount", null);
        if (amount == null) {
            this.handleFeedback(
                    context,
                    MessageConstants.COMMAND_MAX_PLAYERS_QUERY,
                    Component.text(this.plugin().playerManager().getOnlinePlayers().size()),
                    Component.text(Bukkit.getMaxPlayers())
            );
            return;
        }
        feature.setMaxPlayers(amount);
        this.handleFeedback(
                context,
                amount == -1 ? MessageConstants.COMMAND_MAX_PLAYERS_RESET : MessageConstants.COMMAND_MAX_PLAYERS_SUCCESS,
                Component.text(Bukkit.getMaxPlayers())
        );
    }

    @Override
    public String getFeatureID() {
        return "max_players";
    }
}