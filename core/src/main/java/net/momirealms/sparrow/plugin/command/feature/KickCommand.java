package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.KickMessage;
import net.momirealms.sparrow.player.cluster.ClusterPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

// 踢出集群内任意服务器上的玩家. 消息只发往玩家所在的服务器, 由该服务器执行踢出
public final class KickCommand extends BukkitCommandFeature {

    public KickCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerManager().cluster()))
                .optional("reason", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    // -s 只隐藏给执行人的成功提示, 错误照常提示
    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String name = context.get("player");
        ClusterPlayer target = this.plugin().playerManager().cluster().find(name);
        if (target == null) {
            this.handleFeedback(sender, MessageConstants.COMMAND_KICK_OFFLINE, Component.text(name));
            return;
        }
        String reason = context.getOrDefault("reason", "");
        this.plugin().messageBrokerManager().broker().publishOneWay(new KickMessage(target.uuid(), reason, sender.getName()), target.server());
        boolean self = sender instanceof Player player && player.getUniqueId().equals(target.uuid());
        this.handleFeedback(
                context,
                self ? MessageConstants.COMMAND_KICK_SUCCESS_SELF : MessageConstants.COMMAND_KICK_SUCCESS,
                Component.text(target.name()),
                Component.text(target.server())
        );
    }

    @Override
    public String getFeatureID() {
        return "kick";
    }
}