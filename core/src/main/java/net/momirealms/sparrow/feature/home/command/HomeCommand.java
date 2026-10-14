package net.momirealms.sparrow.feature.home.command;

import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jspecify.annotations.NonNull;

public final class HomeCommand extends AbstractHomeCommand {

    public HomeCommand(HomeFeature feature) {
        super(feature);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class)
                .optional(
                        "name",
                        TokenParser.tokenParser(),
                        (context, input) -> super.feature.suggest(context.sender(), input.peekString(), true, this.commandConfig().getPermission())
                )
                .handler(this::execute));
    }

    private void execute(CommandContext<Player> context) {
        // TODO 接入新的传送配置与处理器后实现
    }

    @Override
    public String getFeatureID() {
        return "home";
    }
}
