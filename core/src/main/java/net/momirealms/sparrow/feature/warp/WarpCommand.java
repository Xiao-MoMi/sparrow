package net.momirealms.sparrow.feature.warp;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.teleport.TeleportOptions;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public final class WarpCommand extends BukkitCommandFeature {
    private final WarpFeature feature;

    public WarpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull WarpFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    // 名称用贪婪参数, 中文名称不受 Brigadier 单词规则限制; 送别人过去、指定预热和跳过检查各需要额外的权限
    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        String permission = this.plugin().configurationManager().commandsConfig().configDefinition().command(this.getFeatureID()).getPermission();
        return builder.required("name", StringParser.greedyFlagYieldingStringParser(),
                        SuggestionProvider.blockingStrings((context, input) -> this.feature.suggest(context.sender(), input.remainingInput())))
                .flag(manager.flagBuilder("player").withAliases("p").withComponent(PlayerParser.playerParser()).withPermission(permission + ".other"))
                .flag(manager.flagBuilder("warmup").withComponent(IntegerParser.integerParser(0)).withPermission(permission + ".warmup"))
                .flag(manager.flagBuilder("ignore-check").withPermission(permission + ".ignore-check"))
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        String name = context.get("name");
        Warp warp = this.feature.registry().get(name);
        // 没有权限的 warp 与不存在一样处理
        if (warp == null || !this.feature.visible(context.sender(), warp)) {
            this.handleFeedback(context, MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(name));
            return;
        }
        Player target = context.flags().getValue("player", context.sender() instanceof Player sender ? sender : null);
        if (target == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        TeleportOptions options = this.feature.config().teleportOptions().resolve(target,
                target == context.sender(),
                context.flags().getValue("warmup", null),
                context.flags().hasFlag("ignore-check"));
        this.plugin().playerManager().teleportService().teleport(target, warp.server(), warp.location(), options).thenAccept(result -> {
            var message = switch (result) {
                case SUCCESS -> MessageConstants.COMMAND_WARP_SUCCESS;
                case CONNECTING -> MessageConstants.COMMAND_WARP_CONNECTING;
                case SERVER_OFFLINE -> MessageConstants.COMMAND_WARP_SERVER_OFFLINE;
                case INVALID -> MessageConstants.COMMAND_WARP_INVALID;
                case FAILED -> MessageConstants.COMMAND_TELEPORT_FAILURE;
                // 冷却与取消的原因已经提示给玩家本人
                case COOLDOWN, CANCELLED -> null;
            };
            if (message == null) return;
            this.handleFeedback(context, message, Component.text(target.getName()), Component.text(warp.name()), Component.text(warp.server()));
        }).exceptionally(error -> {
            Throwable cause = error instanceof CompletionException ? error.getCause() : error;
            if (cause instanceof TimeoutException) {
                this.handleFeedback(context, MessageConstants.COMMAND_WARP_TIMEOUT);
            } else {
                this.plugin().logger().warn("Failed to send " + target.getName() + " to warp " + warp.name(), cause);
                this.handleFeedback(context, MessageConstants.COMMAND_TELEPORT_FAILURE, Component.text(target.getName()));
            }
            return null;
        });
    }

    @Override
    public String getFeatureID() {
        return "warp";
    }
}
