package net.momirealms.sparrow.feature.warp.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.feature.warp.WarpFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.teleport.TeleportType;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public final class WarpCommand extends BukkitCommandFeature {
    private final WarpFeature feature;

    public WarpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull WarpFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    // /warp <name> 传送自己, /warp <name> <player> 送别人; 后者还需要命令权限加 .other, 没有时客户端看不到目标参数
    // 名称读到空格为止, 中文名称不受 Brigadier 单词规则限制
    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> named = builder.required(
                "name",
                TokenParser.tokenParser(),
                SuggestionProvider.blockingStrings((context, input) -> this.feature.suggest(context.sender(), input.peekString()))
        );
        String other = this.plugin()
                .configurationManager()
                .commandsConfig()
                .configDefinition()
                .command(this.getFeatureID())
                .getPermission() + ".other";
        manager.command(named.required("player", PlayerParser.playerParser())
                .permission(Permission.allOf(builder.commandPermission(), Permission.of(other)))
                .handler(this::execute));
        manager.command(named.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String name = context.get("name");
        Warp warp = this.feature.registry().get(name);
        // 没有权限的 warp 与不存在一样处理
        if (warp == null || !this.feature.visible(context.sender(), warp)) {
            this.handleFeedback(context, MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(name));
            return;
        }
        Player target = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (target == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        boolean self = target == context.sender();
        this.plugin().teleportService()
                .teleport(target, TeleportType.WARP, warp.server(), warp.location(), self)
                .thenAccept(result -> {
                    TranslatableComponent message = switch (result) {
                        case LOCAL_SUCCESS -> self ? MessageConstants.COMMAND_WARP_SUCCESS_SELF : MessageConstants.COMMAND_WARP_SUCCESS;
                        case REMOTE_SUCCESS -> self ? null : MessageConstants.COMMAND_WARP_SUCCESS;
                        case SERVER_OFFLINE -> MessageConstants.COMMAND_WARP_SERVER_OFFLINE;
                        case INVALID -> MessageConstants.COMMAND_WARP_INVALID;
                        case FAILED -> self ? MessageConstants.COMMAND_TELEPORT_FAILURE_SELF : MessageConstants.COMMAND_TELEPORT_FAILURE;
                        // 被拒绝的原因已经提示给被传送的玩家
                        case REJECTED -> self ? null : MessageConstants.COMMAND_TELEPORT_FAILURE;
                    };
                    if (message == null) {
                        return;
                    }
                    this.handleFeedback(
                            context,
                            message,
                            Component.text(target.getName()),
                            Component.text(warp.name()),
                            Component.text(warp.server())
                    );
                })
                .exceptionally(error -> {
                    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                    if (cause instanceof TimeoutException) {
                        this.handleFeedback(context, MessageConstants.COMMAND_WARP_TIMEOUT);
                    } else {
                        this.plugin()
                                .logger()
                                .warn("Failed to send " + target.getName() + " to warp " + warp.name(), cause);
                        this.handleFeedback(
                                context,
                                self ? MessageConstants.COMMAND_TELEPORT_FAILURE_SELF : MessageConstants.COMMAND_TELEPORT_FAILURE,
                                Component.text(target.getName())
                        );
                    }
                    return null;
                });
    }

    @Override
    public String getFeatureID() {
        return "warp";
    }
}
