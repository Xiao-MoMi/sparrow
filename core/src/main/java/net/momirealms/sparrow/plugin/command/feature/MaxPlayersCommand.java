package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.playerlimit.PlayerLimitFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
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
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.optional("amount", IntegerParser.integerParser(-1))
                .handler(this::execute);
    }

    // 不带参数时查询, -1 恢复为 server.properties 的值
    private void execute(CommandContext<CommandSender> context) {
        PlayerLimitFeature feature = this.plugin().featureManager().feature(PlayerLimitFeature.ID, PlayerLimitFeature.class);
        if (!feature.enabled()) {
            this.handleFeedback(context, MessageConstants.COMMAND_FEATURE_DISABLED, Component.text(PlayerLimitFeature.ID));
            return;
        }
        Integer amount = context.getOrDefault("amount", null);
        if (amount == null) {
            Component online = Component.text(this.plugin().playerManager().getOnlinePlayers().size());
            this.handleFeedback(context, MessageConstants.COMMAND_MAX_PLAYERS_QUERY, online, Component.text(feature.maxPlayers()));
            return;
        }
        feature.maxPlayers(amount);
        this.handleFeedback(context, amount < 0 ? MessageConstants.COMMAND_MAX_PLAYERS_RESET : MessageConstants.COMMAND_MAX_PLAYERS_SUCCESS, Component.text(feature.maxPlayers()));
    }

    @Override
    public String getFeatureID() {
        return "max-players";
    }
}
