package net.momirealms.sparrow.feature.ban.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.feature.ban.BanFeature;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanResult;
import net.momirealms.sparrow.feature.ban.BanTexts;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.PlayerIdentity;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import net.momirealms.sparrow.plugin.command.parser.DurationParser;
import net.momirealms.sparrow.util.IpRange;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
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
        manager.command(builder.required("player", ClusterPlayerParser.clusterPlayerNameParser(this.plugin().playerDirectory()))
                .optional(
                        "reason",
                        StringParser.greedyFlagYieldingStringParser(),
                        SuggestionProvider.blockingStrings((context, input) -> this.feature.config().reasonPresets().keySet())
                )
                .flag(manager.flagBuilder("time").withAliases("t").withComponent(DurationParser.durationParser()))
                .flag(manager.flagBuilder("ip").withAliases("I"))
                .flag(manager.flagBuilder("force"))
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    // -s 隐藏给执行人的成功提示, 也不通知管理员, 错误照常提示
    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String input = context.get("player");
        String reason = this.feature.config().getReason(context.getOrDefault("reason", ""));
        if (reason.codePointCount(0, reason.length()) > BanRecord.MAX_REASON_LENGTH) {
            this.handleFeedback(sender, MessageConstants.COMMAND_BAN_REASON_TOO_LONG, Component.text(BanRecord.MAX_REASON_LENGTH));
            return;
        }
        Duration time = context.flags().getValue("time", null);
        long expiresAt = time == null ? 0 : Math.addExact(System.currentTimeMillis(), time.toMillis());
        boolean withIp = context.flags().hasFlag("ip");
        boolean silent = context.flags().hasFlag("silent");
        boolean force = context.flags().hasFlag("force");
        this.feature.resolvePlayer(input)
                .thenCompose(found -> {
                    if (found.isEmpty()) {
                        this.handleFeedback(sender, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(input));
                        return CompletableFuture.completedFuture(null);
                    }
                    PlayerIdentity target = found.get();
                    if (!withIp) {
                        return this.feature.ban(target, null, reason, expiresAt, sender.getName(), silent, force)
                                .thenAccept(result -> this.sendResult(context, result));
                    }
                    // 在线玩家进服时已经写入当前 IP, 数据库中的记录就是最近一次登录的 IP
                    return this.plugin().dataStorage()
                            .loadPlayer(target.uuid())
                            .thenCompose(data -> {
                                String ip = data.map(PlayerData::lastLoginIp).orElse(null);
                                if (ip == null) {
                                    this.handleFeedback(sender, MessageConstants.COMMAND_NO_ADDRESS, Component.text(target.name()));
                                    return CompletableFuture.completedFuture(null);
                                }
                                return this.feature.ban(target, IpRange.parse(ip), reason, expiresAt, sender.getName(), silent, force)
                                        .thenAccept(result -> this.sendResult(context, result));
                            });
                })
                .exceptionally(error -> {
                    this.plugin().logger().warn("Failed to ban " + input, error);
                    this.handleFeedback(sender, MessageConstants.COMMAND_DATABASE_FAILED);
                    return null;
                });
    }

    // 覆盖被拒绝时照常提示错误, 成功反馈遵守 -s
    private void sendResult(CommandContext<CommandSender> context, BanResult result) {
        BanRecord record = result.record();
        BanRecord previous = result.previous();
        if (result.status() == BanResult.Status.REPLACEMENT_REJECTED) {
            this.handleFeedback(
                    context.sender(),
                    MessageConstants.COMMAND_BAN_REPLACEMENT_REJECTED,
                    Component.text(record.display()),
                    BanTexts.id(previous.id())
            );
            return;
        }
        long now = System.currentTimeMillis();
        boolean self = context.sender() instanceof Player player && player.getUniqueId().equals(record.player());
        this.handleFeedback(
                context,
                self ? MessageConstants.COMMAND_BAN_SUCCESS_SELF : MessageConstants.COMMAND_BAN_SUCCESS,
                Component.text(record.display()),
                BanTexts.reason(record.reason()),
                BanTexts.expiry(record.expiresAt(), now),
                BanTexts.id(record.id())
        );
        if (previous != null) {
            this.handleFeedback(
                    context,
                    self ? MessageConstants.COMMAND_BAN_REPLACED_SELF : MessageConstants.COMMAND_BAN_REPLACED,
                    Component.text(record.display())
            );
        }
    }

    @Override
    public String getFeatureID() {
        return "ban";
    }
}