package net.momirealms.sparrow.feature.home;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jspecify.annotations.NonNull;

public final class SetHomeCommand extends AbstractHomeCommand {

    public SetHomeCommand(HomeFeature feature) {
        super(feature);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<Player> cmd = builder.senderType(Player.class)
                .optional("name", TokenParser.tokenParser(), (context, input) ->
                        this.feature.suggest(context.sender(), input.peekString(), false, this.commandConfig().getPermission())
                ).handler(this::execute);
        manager.command(cmd);
    }

    private void execute(CommandContext<Player> context) {
        Player player = context.sender();
        String name = context.getOrDefault("name", this.feature.config().defaultName());
        int limit = this.feature.limit(player);
        this.feature.service().set(player.getUniqueId(), name, ServerConfig.serverId(), WorldLocation.from(player.getLocation()), limit).thenAccept(result -> {
            var message = switch (result.status()) {
                case CREATED -> MessageConstants.COMMAND_SET_HOME_CREATED;
                case DUPLICATE_NAME -> MessageConstants.COMMAND_SET_HOME_EXISTS;
                case NOT_FOUND -> MessageConstants.COMMAND_HOME_UNKNOWN;
                case INVALID_NAME -> MessageConstants.COMMAND_HOME_INVALID_NAME;
                case LIMIT_REACHED -> MessageConstants.COMMAND_SET_HOME_LIMIT;
                case UPDATED -> throw new AssertionError();
            };
            this.handleFeedback(context, message, Component.text(name), Component.text(limit), Component.text(Home.MAX_NAME_LENGTH));
        }).exceptionally(error -> { this.failed(player, error); return null; });
    }

    @Override
    public String getFeatureID() {
        return "set-home";
    }
}