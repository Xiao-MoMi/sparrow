package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.proxy.DisconnectRequest;
import net.momirealms.sparrow.player.message.proxy.DisconnectResponse;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BatchCommandFeedback;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class KickCommand extends BukkitCommandFeature {

    public KickCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", GlobalPlayerParser.clusterPlayersParser())
                .optional("reason", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        List<String> targets = context.get("player");
        String reason = context.getOrDefault("reason", "");
        BatchCommandFeedback feedback = new BatchCommandFeedback(this, context, targets.size());
        for (String input : targets) {
            this.kick(context, input, reason, feedback);
        }
    }

    private void kick(CommandContext<CommandSender> context, String input, String reason, BatchCommandFeedback feedback) {
        CommandSender sender = context.sender();
        UUID uuid = UUIDUtils.parse(input);
        OfflineSparrowPlayer target = uuid == null
                ? this.plugin().playerManager().getGlobalOnlinePlayer(input)
                : this.plugin().playerManager().getGlobalOnlinePlayer(uuid);
        if (target == null) {
            feedback.failureAlways(MessageConstants.COMMAND_KICK_OFFLINE, Component.text(input));
            return;
        }
        String server = target.getCurrentServerId();
        if (server == null) {
            feedback.failureAlways(MessageConstants.COMMAND_KICK_OFFLINE, Component.text(target.name()));
            return;
        }
        Component text = reason.isEmpty() ? MessageConstants.KICK_REASON_NONE : Component.text(reason);
        Component screen = this.plugin().translationManager().render(MessageConstants.KICK_SCREEN.arguments(text, Component.text(sender.getName())), target.locale());
        boolean self = sender instanceof Player player && player.getUniqueId().equals(target.uniqueId());
        this.disconnect(target, screen).whenComplete((disconnected, error) -> {
            if (error != null) {
                this.plugin().logger().warn("Failed to kick " + target.name(), error);
                feedback.failureAlways(MessageConstants.COMMAND_KICK_FAILED, Component.text(target.name()));
            } else if (!disconnected) {
                feedback.failureAlways(MessageConstants.COMMAND_KICK_OFFLINE, Component.text(target.name()));
            } else {
                feedback.success(
                        self ? MessageConstants.COMMAND_KICK_SUCCESS_SELF : MessageConstants.COMMAND_KICK_SUCCESS,
                        Component.text(target.name()),
                        Component.text(server)
                );
            }
        });
    }

    private CompletableFuture<Boolean> disconnect(OfflineSparrowPlayer target, Component screen) {
        return this.plugin().messageBrokerManager().proxyBroker()
                .publishTwoWay(new DisconnectRequest(target.uniqueId(), AdventureHelper.componentToJson(screen)), "proxy")
                .orTimeout(5, TimeUnit.SECONDS)
                .thenApply(DisconnectResponse::disconnected);
    }

    @Override
    public String getFeatureID() {
        return "kick";
    }
}