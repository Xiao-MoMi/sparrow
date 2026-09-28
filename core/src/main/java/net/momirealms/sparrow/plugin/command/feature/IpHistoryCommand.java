package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.cluster.ClusterPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.TextPage;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import net.momirealms.sparrow.util.IpRange;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 列出最近一次登录 IP 在指定范围内的玩家, 可以用来查小号. 目标是玩家时使用数据库记录的最近登录 IP.
 * 每页只查询总数和当前页.
 */
public final class IpHistoryCommand extends BukkitCommandFeature {
    private static final int PAGE_SIZE = 8;
    private static final String STATUS_SYMBOL = "●";

    public IpHistoryCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.required("target", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerManager().cluster()))
                .optional("page", IntegerParser.integerParser(1))
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String input = context.get("target");
        int page = context.getOrDefault("page", 1);
        CompletableFuture<Optional<IpRange>> resolved;
        if (IpRange.looksLikeIp(input)) {
            try {
                resolved = CompletableFuture.completedFuture(Optional.of(IpRange.parse(input)));
            } catch (IllegalArgumentException exception) {
                this.handleFeedback(sender, MessageConstants.COMMAND_INVALID_IP, Component.text(input));
                return;
            }
        } else {
            // 输入是 UUID 时直接读取数据, 否则先按名字解析出 UUID
            UUID uuid = UUIDUtils.parse(input);
            CompletableFuture<Optional<PlayerData>> loading = uuid != null
                    ? this.plugin().dataStorage().loadPlayer(uuid)
                    : this.plugin().playerManager().resolvePlayer(input).thenCompose(found -> found.isEmpty()
                            ? CompletableFuture.completedFuture(Optional.empty())
                            : this.plugin().dataStorage().loadPlayer(found.get().uuid()));
            resolved = loading.thenApply(data -> data.map(PlayerData::lastLoginIp).map(IpRange::parse));
        }
        resolved.thenCompose(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(sender, MessageConstants.COMMAND_NO_ADDRESS, Component.text(input));
                return CompletableFuture.completedFuture(null);
            }
            IpRange range = found.get();
            return TextPage.load(() -> this.plugin().dataStorage().countPlayersOnIp(range), (offset, limit) -> this.plugin().dataStorage().listPlayersOnIp(range, offset, limit), page - 1, PAGE_SIZE)
                    .thenAccept(result -> this.render(sender, input, range, result));
        }).exceptionally(error -> {
            this.plugin().logger().warn("Failed to list players on IP " + input, error);
            this.handleFeedback(sender, MessageConstants.COMMAND_DATABASE_FAILED);
            return null;
        });
    }

    private void render(CommandSender sender, String input, IpRange range, TextPage<PlayerData> page) {
        Component title = MessageConstants.COMMAND_IP_HISTORY_TITLE.build().arguments(Component.text(input), Component.text(range.toString()));
        Component panel = CommandPanel.tr("header", title, Component.text(page.index() + 1), Component.text(page.count()), Component.text(page.total()));
        List<PlayerData> players = page.content();
        int size = players.size();
        for (int i = 0; i < size; i++) {
            panel = panel.append(Component.newline()).append(this.row(sender, players.get(i)));
        }
        if (size == 0) {
            panel = panel.append(Component.newline()).append(CommandPanel.tr("empty"));
        }
        panel = panel.append(Component.newline()).append(CommandPanel.navigation(this.commandManager(), sender, this.getFeatureID(), page, index -> input + " " + index));
        CommandPanel.send(this.commandManager(), sender, panel);
    }

    private Component row(CommandSender sender, PlayerData data) {
        boolean player = sender instanceof Player;
        ClusterPlayer online = this.plugin().playerManager().cluster().find(data.player());
        Component name = Component.text(data.name());
        Component status = online != null
                ? MessageConstants.COMMAND_IP_HISTORY_ONLINE.build().arguments(Component.text(online.server()))
                : MessageConstants.COMMAND_IP_HISTORY_OFFLINE.build().arguments(Component.text(data.lastLogoutServer() == null ? "-" : data.lastLogoutServer()));
        String ip = data.lastLoginIp() == null ? "-" : data.lastLoginIp();
        Component time = Component.text(player ? CommandPanel.shortTime(data.lastLogin()) : CommandPanel.fullTime(data.lastLogin()));
        // 玩家看到固定宽度的圆点, 绿色在线, 灰色离线, 文字说明放进悬浮
        if (player) {
            name = name.hoverEvent(Component.text(data.player().toString())).clickEvent(ClickEvent.copyToClipboard(data.player().toString()));
            status = Component.text(STATUS_SYMBOL, online != null ? NamedTextColor.GREEN : NamedTextColor.GRAY).hoverEvent(status);
        }
        return MessageConstants.COMMAND_IP_HISTORY_ROW.build().arguments(status, name, time, Component.text(ip));
    }

    @Override
    public String getFeatureID() {
        return "ip-history";
    }
}
