package net.momirealms.sparrow.feature.ban.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.ban.BanFeature;
import net.momirealms.sparrow.feature.ban.BanTarget;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

// 目标可以是 #ID、玩家名、UUID 或 IP. IP 只解除段完全相同的纯 IP 封禁, 账号加 IP 的封禁用玩家或 ID 解除
public final class UnbanCommand extends BukkitCommandFeature {
    private final BanFeature feature;

    public UnbanCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull BanFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("target", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerDirectory()))
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    // -s 隐藏给执行人的成功提示, 也不通知管理员, 其余提示照常发送
    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String input = context.get("target");
        boolean silent = context.flags().hasFlag("silent");
        CompletableFuture<Optional<BanTarget>> resolved;
        try {
            resolved = this.feature.resolveTarget(input);
        } catch (IllegalArgumentException exception) {
            this.handleFeedback(sender, MessageConstants.COMMAND_INVALID_TARGET, Component.text(input));
            return;
        }
        resolved.thenCompose(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(sender, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(input));
                return CompletableFuture.completedFuture(null);
            }
            BanTarget target = found.get();
            return this.feature.unban(target, sender.getName(), silent).thenAccept(revoked -> {
                if (revoked.isEmpty()) {
                    this.handleFeedback(sender, MessageConstants.COMMAND_UNBAN_NONE, Component.text(target.display()));
                } else {
                    this.handleFeedback(
                            context,
                            MessageConstants.COMMAND_UNBAN_SUCCESS,
                            Component.text(target.display()),
                            Component.text(revoked.size())
                    );
                }
            });
        }).exceptionally(error -> {
            this.plugin().logger().warn("Failed to unban " + input, error);
            this.handleFeedback(sender, MessageConstants.COMMAND_DATABASE_FAILED);
            return null;
        });
    }

    @Override
    public String getFeatureID() {
        return "unban";
    }
}