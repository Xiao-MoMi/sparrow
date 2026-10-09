package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.redis.proxy.DisconnectRequest;
import net.momirealms.sparrow.redis.proxy.DisconnectResponse;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.VersionHelper;
import net.momirealms.sparrow.cluster.PlayerPresence;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class KickCommand extends BukkitCommandFeature {

    public KickCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", ClusterPlayerParser.clusterPlayersParser(this.plugin().playerDirectory()))
                .optional("reason", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    // -s 只隐藏给执行人的成功提示, 错误照常提示
    private void execute(CommandContext<CommandSender> context) {
        List<String> targets = context.get("player");
        String reason = context.getOrDefault("reason", "");
        for (int i = 0, size = targets.size(); i < size; i++) {
            this.kick(context, targets.get(i), reason);
        }
    }

    private void kick(CommandContext<CommandSender> context, String name, String reason) {
        CommandSender sender = context.sender();
        PlayerPresence target = this.plugin().playerDirectory().find(name);
        if (target == null) {
            this.handleFeedback(sender, MessageConstants.COMMAND_KICK_OFFLINE, Component.text(name));
            return;
        }
        Component text = reason.isEmpty() ? MessageConstants.KICK_REASON_NONE : Component.text(reason);
        Component screen = this.plugin().translationManager().render(MessageConstants.KICK_SCREEN.arguments(text, Component.text(sender.getName())), target.locale());
        boolean self = sender instanceof Player player && player.getUniqueId().equals(target.uuid());
        this.disconnect(target, screen).whenComplete((disconnected, error) -> {
            if (error != null) {
                this.plugin().logger().warn("Failed to kick " + target.name(), error);
                this.handleFeedback(sender, MessageConstants.COMMAND_KICK_FAILED, Component.text(target.name()));
            } else if (!disconnected) {
                this.handleFeedback(sender, MessageConstants.COMMAND_KICK_OFFLINE, Component.text(target.name()));
            } else {
                this.handleFeedback(
                        context,
                        self ? MessageConstants.COMMAND_KICK_SUCCESS_SELF : MessageConstants.COMMAND_KICK_SUCCESS,
                        Component.text(target.name()),
                        Component.text(target.server())
                );
            }
        });
    }

    private CompletableFuture<Boolean> disconnect(PlayerPresence target, Component screen) {
        if (VersionHelper.isBehindProxy()) {
            return this.plugin().messageBrokerManager().proxyBroker()
                    .publishTwoWay(new DisconnectRequest(target.uuid(), AdventureHelper.componentToJson(screen)), "proxy")
                    .orTimeout(5, TimeUnit.SECONDS)
                    .thenApply(DisconnectResponse::disconnected);
        }
        SparrowPlayer player = this.plugin().playerManager().getPlayer(target.uuid());
        if (player == null) return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        this.plugin().scheduler().platform().run(() -> {
            player.kick(screen, false);
            result.complete(true);
        }, () -> result.complete(false), player.platformPlayer());
        return result;
    }

    @Override
    public String getFeatureID() {
        return "kick";
    }
}