package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import net.momirealms.sparrow.util.DateTimeUtils;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// 查询玩家最近一次进服使用的 IP 与时间
public final class IpCommand extends BukkitCommandFeature {

    public IpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerDirectory()))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String input = context.get("player");
        // 输入是 UUID 时直接读取数据, 否则先按名字解析出 UUID
        UUID uuid = UUIDUtils.parse(input);
        CompletableFuture<Optional<PlayerData>> loading = uuid != null
                ? this.plugin().dataStorage().loadPlayer(uuid)
                : this.plugin().playerLookup().resolvePlayer(input).thenCompose(found ->
                        found.isEmpty()
                        ? CompletableFuture.completedFuture(Optional.empty())
                        : this.plugin().dataStorage().loadPlayer(found.get().uuid())
        );
        loading.thenAccept(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(sender, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(input));
                return;
            }
            PlayerData data = found.get();
            String ip = data.lastLoginIp();
            if (ip == null) {
                this.handleFeedback(sender, MessageConstants.COMMAND_NO_ADDRESS, Component.text(data.name()));
                return;
            }
            // 点击 IP 复制, 旁边的按钮查询同 IP 的玩家
            Component address = Component.text(ip).hoverEvent(Component.text(ip)).clickEvent(ClickEvent.copyToClipboard(ip));
            Component history = new CommandPanel(this.commandManager(), sender).run(CommandPanel.label("ip_history"), "ip-history", ip).build();
            this.handleFeedback(
                    sender,
                    MessageConstants.COMMAND_IP_SUCCESS,
                    Component.text(data.name()),
                    address,
                    Component.text(DateTimeUtils.fullTime(data.lastLogin())),
                    history
            );
        }).exceptionally(error -> {
            this.plugin().logger().warn("Failed to query the last IP of " + input, error);
            this.handleFeedback(sender, MessageConstants.COMMAND_DATABASE_FAILED);
            return null;
        });
    }

    @Override
    public String getFeatureID() {
        return "ip";
    }
}