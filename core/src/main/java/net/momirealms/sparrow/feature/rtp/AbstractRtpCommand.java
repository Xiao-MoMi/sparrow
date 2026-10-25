package net.momirealms.sparrow.feature.rtp;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.teleport.position.rtp.RtpArea;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.permission.PermissionResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

abstract class AbstractRtpCommand extends BukkitCommandFeature {

    AbstractRtpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    // 指定了其他玩家时还需要 .other 权限
    void checkOtherPermission(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Permission otherPermission, @NotNull CommandContext<CommandSender> context) {
        if (!context.contains("player")) return;
        PermissionResult permission = manager.testPermission(context.sender(), otherPermission);
        if (!permission.allowed()) throw new NoPermissionException(permission, context.sender(), List.copyOf(context.command().components()));
    }

    // 被传送者收到过程中的全部提示; 发起者另有其人时收到结果汇总, 自己传送自己且成功时收到成功提示
    void start(
            @NotNull CommandContext<CommandSender> context,
            @NotNull String target,
            @Nullable String permission,
            @Nullable String server,
            @NotNull List<RtpArea> areas
    ) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        BukkitSparrowPlayer traveler = this.plugin().playerManager().getOnlinePlayer(player);
        CompletableFuture<@Nullable Component> outcome;
        // 区域权限按被传送者判断
        if (permission != null && !player.hasPermission(permission)) {
            traveler.sendTranslatedMessage(MessageConstants.TELEPORT_REJECTED_REGION);
            outcome = CompletableFuture.completedFuture(MessageConstants.TELEPORT_REJECTED_REGION);
        } else {
            outcome = this.plugin().featureManager().feature(RtpFeature.ID, RtpFeature.class).teleport(traveler, server, areas);
        }
        boolean self = player == context.sender();
        outcome.thenAccept(reason -> {
            if (reason == null) {
                if (self) {
                    this.handleFeedback(context, MessageConstants.COMMAND_RTP_SUCCESS_SELF);
                } else {
                    this.handleFeedback(context, MessageConstants.COMMAND_RTP_SUCCESS, Component.text(player.getName()), Component.text(target));
                }
            } else if (!self) {
                this.handleFeedback(context, MessageConstants.COMMAND_RTP_FAILED, Component.text(player.getName()), reason);
            }
        });
    }
}