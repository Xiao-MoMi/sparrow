package net.momirealms.sparrow.feature.ban.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.ban.BanFeature;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanService;
import net.momirealms.sparrow.feature.ban.BanTexts;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.suggestion.Suggestion;

import java.util.List;
import java.util.concurrent.CompletableFuture;

abstract class AbstractBanCommand extends BukkitCommandFeature {
    protected final BanFeature feature;

    protected AbstractBanCommand(CommandManager manager, SparrowPlugin plugin, BanFeature feature) {
        super(manager, plugin);
        this.feature = feature;
    }

    protected CompletableFuture<List<Suggestion>> suggestPlayers(CommandContext<CommandSender> context, CommandInput input) {
        return CompletableFuture.completedFuture(this.plugin().playerManager().registry.suggestGlobalPlayers(input.peekString()));
    }

    protected void feedback(CommandContext<CommandSender> context, String input, BanService.Result result) {
        BanRecord record = result.record();
        switch (result.status()) {
            case CREATED, REPLACED -> {
                this.handleFeedback(
                        context,
                        result.status() == BanService.Status.CREATED ? MessageConstants.COMMAND_BAN_CREATED : MessageConstants.COMMAND_BAN_REPLACED,
                        BanTexts.target(record),
                        BanTexts.reason(record.reason()),
                        BanTexts.expiry(record.expiresAt(), System.currentTimeMillis()),
                        BanTexts.id(record.id())
                );
            }
            case REVOKED -> this.handleFeedback(context, MessageConstants.COMMAND_UNBAN_SUCCESS, BanTexts.target(record), BanTexts.id(record.id()));
            case ALREADY_BANNED -> {
                BanRecord previous = result.previous();
                if (previous == null) {
                    this.handleFeedback(context.sender(), MessageConstants.COMMAND_BAN_ALREADY_BANNED, Component.text(input));
                } else {
                    this.handleFeedback(context.sender(), MessageConstants.COMMAND_BAN_ALREADY_BANNED_ID, BanTexts.target(previous), BanTexts.id(previous.id()));
                }
            }
            case NOT_FOUND -> this.handleFeedback(context.sender(), MessageConstants.COMMAND_UNBAN_NONE, Component.text(input));
            case UNKNOWN_PLAYER -> this.handleFeedback(context.sender(), MessageConstants.COMMAND_BAN_UNKNOWN_PLAYER, Component.text(input));
            case NO_ADDRESS -> this.handleFeedback(context.sender(), MessageConstants.COMMAND_BAN_NO_ADDRESS, Component.text(input));
            case UNSUPPORTED_ADDRESS -> this.handleFeedback(context.sender(), MessageConstants.COMMAND_BAN_UNSUPPORTED_ADDRESS, Component.text(input));
            case INVALID_IP -> this.handleFeedback(context.sender(), MessageConstants.COMMAND_BAN_INVALID_IP, Component.text(input));
            case INVALID_DURATION -> this.handleFeedback(context.sender(), MessageConstants.COMMAND_BAN_INVALID_DURATION);
            case REASON_TOO_LONG -> this.handleFeedback(context.sender(), MessageConstants.COMMAND_BAN_REASON_TOO_LONG, Component.text(BanRecord.MAX_REASON_LENGTH));
        }
    }

    protected enum Branch {
        PLAYER, PLAYER_IP, IP
    }
}