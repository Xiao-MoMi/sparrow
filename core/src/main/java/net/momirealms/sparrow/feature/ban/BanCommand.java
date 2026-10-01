package net.momirealms.sparrow.feature.ban;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import net.momirealms.sparrow.plugin.command.parser.DurationParser;
import net.momirealms.sparrow.util.IpRange;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

// 按玩家名或 UUID 封禁账号, -I 同时封禁该玩家最近一次登录的 IP
public final class BanCommand extends BukkitCommandFeature {
    private final BanFeature feature;

    public BanCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull BanFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerManager().cluster()))
                .optional("reason", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("time").withAliases("t").withComponent(DurationParser.durationParser()))
                .flag(manager.flagBuilder("ip").withAliases("I"))
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    // -s 隐藏给执行人的成功提示, 也不通知管理员, 错误照常提示
    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String input = context.get("player");
        String reason = context.getOrDefault("reason", "");
        if (reason.codePointCount(0, reason.length()) > BanRecord.MAX_REASON_LENGTH) {
            this.handleFeedback(sender, MessageConstants.COMMAND_BAN_REASON_TOO_LONG, Component.text(BanRecord.MAX_REASON_LENGTH));
            return;
        }
        Duration time = context.flags().getValue("time", null);
        long expiresAt = time == null ? 0 : Math.addExact(System.currentTimeMillis(), time.toMillis());
        boolean withIp = context.flags().hasFlag("ip");
        boolean silent = context.flags().hasFlag("silent");
        this.feature.resolvePlayer(input).thenCompose(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(sender, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(input));
                return CompletableFuture.completedFuture(null);
            }
            BanTarget.PlayerTarget target = found.get();
            if (!withIp) {
                return this.feature.ban(target, null, reason, expiresAt, sender.getName(), silent).thenAccept(result -> this.sendResult(context, result));
            }
            // 在线玩家进服时已经写入当前 IP, 数据库中的记录就是最近一次登录的 IP
            return this.plugin().dataStorage().loadPlayer(target.uuid()).thenCompose(data -> {
                String ip = data.map(PlayerData::lastLoginIp).orElse(null);
                if (ip == null) {
                    this.handleFeedback(sender, MessageConstants.COMMAND_NO_ADDRESS, Component.text(target.name()));
                    return CompletableFuture.completedFuture(null);
                }
                return this.feature.ban(target, IpRange.parse(ip), reason, expiresAt, sender.getName(), silent).thenAccept(result -> this.sendResult(context, result));
            });
        }).exceptionally(error -> {
            this.plugin().logger().warn("Failed to ban " + input, error);
            this.handleFeedback(sender, MessageConstants.COMMAND_DATABASE_FAILED);
            return null;
        });
    }

    // 覆盖了旧封禁时追加一行提示
    private void sendResult(CommandContext<CommandSender> context, BanFeature.Result result) {
        BanRecord record = result.record();
        this.handleFeedback(context, MessageConstants.COMMAND_BAN_SUCCESS, Component.text(record.display()), BanTexts.reason(record.reason()),
                BanTexts.expiry(record.expiresAt(), System.currentTimeMillis()), BanTexts.id(record.id()));
        if (result.replaced()) {
            this.handleFeedback(context, MessageConstants.COMMAND_BAN_REPLACED, Component.text(record.display()));
        }
    }

    @Override
    public String getFeatureID() {
        return "ban";
    }
}
