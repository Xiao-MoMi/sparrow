package net.momirealms.sparrow.plugin.command.feature;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.message.BroadcastMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class BroadcastCommand extends BukkitCommandFeature {

    public BroadcastCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("message", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String message = context.get("message");
        this.plugin().messageBrokerManager()
                .publishOneWay(new BroadcastMessage(message), "")
                .whenComplete((ignored, error) -> this.handleFeedback(context, MessageConstants.COMMAND_BROADCAST_SENT));
    }

    @Override
    public String getFeatureID() {
        return "broadcast";
    }
}