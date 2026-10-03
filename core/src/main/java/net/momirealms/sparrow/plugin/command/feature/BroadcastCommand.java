package net.momirealms.sparrow.plugin.command.feature;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BroadcastMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
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
                .flag(manager.flagBuilder("legacy-color").withAliases("l"))
                .flag(manager.flagBuilder("parse").withAliases("p"))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String message = context.get("message");
        PluginConfig.TextOptions text = PluginConfig.text();
        boolean legacy = text.parseLegacyColor() || context.flags().hasFlag("legacy-color");
        boolean placeholders = text.parsePlaceholder() || context.flags().hasFlag("parse");
        this.plugin().messageBrokerManager().broker().publishOneWay(new BroadcastMessage(message, legacy, placeholders), "");
        this.handleFeedback(context, MessageConstants.COMMAND_BROADCAST_SENT);
    }

    @Override
    public String getFeatureID() {
        return "broadcast";
    }
}
