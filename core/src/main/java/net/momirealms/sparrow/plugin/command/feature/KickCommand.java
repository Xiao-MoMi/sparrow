package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.redis.message.player.KickMessage;
import net.momirealms.sparrow.cluster.PlayerPresence;
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

import java.util.List;

// 踢出集群内任意服务器上的玩家. 消息只发往玩家所在的服务器, 由该服务器执行踢出
public final class KickCommand extends BukkitCommandFeature {

    public KickCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", ClusterPlayerParser.clusterPlayersParser(this.plugin().playerDirectory()))
                .optional("reason", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    // -s 只隐藏给执行人的成功提示, 错误照常提示
    private void execute(CommandContext<CommandSender> context) {
        List<String> targets = context.get("player");
        String reason = context.getOrDefault("reason", "");
        for (int i = 0, size = targets.size(); i < size; i++) {
            this.kick(context, targets.get(i), reason);
        }
    }

    private void kick(CommandContext<CommandSender> context, String name, String reason) {
        CommandSender sender = context.sender();
        PlayerPresence target = this.plugin().playerDirectory().find(name);
        if (target == null) {
            this.handleFeedback(sender, MessageConstants.COMMAND_KICK_OFFLINE, Component.text(name));
            return;
        }
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