package net.momirealms.sparrow.feature.ban;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.DurationParser;
import net.momirealms.sparrow.util.IpRange;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.time.Duration;

// 按 IP 或通配 IP 封禁, 使用这些 IP 的在线账号会被一并踢出
public final class BanIpCommand extends BukkitCommandFeature {
    private final BanFeature feature;

    public BanIpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull BanFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("ip", StringParser.stringParser())
                .optional("reason", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("time").withAliases("t").withComponent(DurationParser.durationParser()))
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    // -s 隐藏给执行人的成功提示, 也不通知管理员, 错误照常提示
    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String input = context.get("ip");
        IpRange range;
        try {
            range = IpRange.parse(input);
        } catch (IllegalArgumentException exception) {
            this.handleFeedback(sender, MessageConstants.COMMAND_INVALID_IP, Component.text(input));
            return;
        }
        String reason = context.getOrDefault("reason", "");
        if (reason.codePointCount(0, reason.length()) > BanRecord.MAX_REASON_LENGTH) {
            this.handleFeedback(sender, MessageConstants.COMMAND_BAN_REASON_TOO_LONG, Component.text(BanRecord.MAX_REASON_LENGTH));
            return;
        }
        Duration time = context.flags().getValue("time", null);
        long expiresAt = time == null ? 0 : Math.addExact(System.currentTimeMillis(), time.toMillis());
        boolean silent = context.flags().hasFlag("silent");
        this.feature.ban(null, range, reason, expiresAt, sender.getName(), silent).thenAccept(result -> {
            BanRecord record = result.record();
            this.handleFeedback(context, MessageConstants.COMMAND_BAN_SUCCESS, Component.text(record.display()), BanTexts.reason(record.reason()),
                    BanTexts.expiry(record.expiresAt(), System.currentTimeMillis()), BanTexts.id(record.id()));
            if (result.replaced()) {
                this.handleFeedback(context, MessageConstants.COMMAND_BAN_REPLACED, Component.text(record.display()));
            }
        }).exceptionally(error -> {
            this.plugin().logger().warn("Failed to ban IP " + input, error);
            this.handleFeedback(sender, MessageConstants.COMMAND_DATABASE_FAILED);
            return null;
        });
    }

    @Override
    public String getFeatureID() {
        return "ban-ip";
    }
}
