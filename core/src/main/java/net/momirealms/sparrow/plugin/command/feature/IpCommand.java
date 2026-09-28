package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.PlayerRef;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

// 查询玩家最近一次进服使用的 IP 与时间
public final class IpCommand extends BukkitCommandFeature {

    public IpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.required("player", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerManager().cluster()))
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String input = context.get("player");
        LookupSupport.resolvePlayer(this.plugin(), input).thenCompose(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(sender, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(input));
                return CompletableFuture.completedFuture(null);
            }
            PlayerRef player = found.get();
            return this.plugin().dataStorage().loadPlayer(player.uuid()).thenAccept(data -> {
                String ip = data.map(value -> value.lastLoginIp()).orElse(null);
                if (ip == null) {
                    this.handleFeedback(sender, MessageConstants.COMMAND_NO_ADDRESS, Component.text(player.name()));
                    return;
                }
                // 点击 IP 复制, 旁边的按钮查询同 IP 的玩家
                Component address = Component.text(ip).hoverEvent(Component.text(ip)).clickEvent(ClickEvent.copyToClipboard(ip));
                Component history = CommandPanel.action(this.commandManager(), sender, "ip_history", "ip-history", ip, false, null);
                this.handleFeedback(sender, MessageConstants.COMMAND_IP_SUCCESS, Component.text(player.name()), address,
                        Component.text(CommandPanel.fullTime(data.get().lastLogin())), history);
            });
        }).exceptionally(error -> LookupSupport.failed(this, sender, error));
    }

    @Override
    public String getFeatureID() {
        return "ip";
    }
}
