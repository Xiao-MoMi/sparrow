package net.momirealms.sparrow.feature.head;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.SinglePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.SinglePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.permission.PermissionResult;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class HeadCommand extends BukkitCommandFeature {

    public HeadCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        Permission otherPermission = this.otherPermission(builder);
        manager.command(builder
                .optional(
                        "source",
                        StringParser.stringParser(),
                        (context, input) -> CompletableFuture.completedFuture(this.plugin().playerManager().registry.suggestGlobalPlayers(input.peekString()))
                )
                .optional("amount", IntegerParser.integerParser(1, 6400))
                .optional("player", SinglePlayerSelectorParser.singlePlayerSelectorParser())
                .flag(manager.flagBuilder("force"))
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(context -> {
                    if (context.contains("player")) {
                        PermissionResult permission = manager.testPermission(context.sender(), otherPermission);
                        if (!permission.allowed()) {
                            throw new NoPermissionException(permission, context.sender(), List.copyOf(context.command().components()));
                        }
                    }
                    this.execute(context);
                }));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        SparrowPlugin plugin = this.plugin();
        HeadFeature feature = plugin.featureManager().feature(HeadFeature.ID, HeadFeature.class);
        String source = context.getOrDefault("source", context.sender() instanceof Player player ? player.getName() : null);
        if (source == null) {
            this.handleFeedback(context.sender(), MessageConstants.COMMAND_HEAD_SOURCE_REQUIRED);
            return;
        }
        SinglePlayerSelector selector = context.getOrDefault("player", null);
        Player target = selector != null ? selector.single() : (context.sender() instanceof Player player ? player : null);
        if (target == null) {
            this.handleFeedback(context.sender(), MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        BukkitSparrowPlayer sender = context.sender() instanceof Player player ? plugin.playerManager().getOnlinePlayer(player) : null;
        long generation = feature.generation();
        int amount = context.getOrDefault("amount", 1);
        CompletableFuture<HeadData> future;
        try {
            boolean force = context.flags().hasFlag("force");
            if (source.length() == 32 || source.length() == 36) {
                future = feature.fetchByUuid(UUIDUtils.fromString(source), force);
            } else {
                future = feature.fetchByName(source, force);
            }
        } catch (IllegalArgumentException exception) {
            this.handleFeedback(context.sender(), MessageConstants.COMMAND_HEAD_INVALID, Component.text(source));
            return;
        }
        future.whenComplete((data, error) -> {
            if (context.sender() instanceof Player player && (sender == null || plugin.playerManager().getOnlinePlayer(player) != sender)) {
                return;
            }
            if (error != null) {
                Throwable cause = error;
                while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                TranslatableComponent message = this.failure(cause);
                if (message == MessageConstants.COMMAND_HEAD_FAILURE || message == MessageConstants.COMMAND_HEAD_INVALID_RESPONSE) {
                    plugin.logger().warn("Failed to fetch a head", cause);
                }
                // 超时和失败始终反馈; --silent 只隐藏成功提示.
                this.handleFeedback(context.sender(), message, Component.text(source));
                return;
            }
            if (feature.generation() != generation) {
                this.handleFeedback(context.sender(), MessageConstants.COMMAND_HEAD_CANCELLED);
                return;
            }
            if (data == null) {
                this.handleFeedback(context.sender(), MessageConstants.COMMAND_HEAD_NOT_FOUND, Component.text(source));
                return;
            }
            plugin.scheduler().platform().run(() -> {
                if (feature.give(target, data, amount, generation)) {
                    this.handleFeedback(
                            context,
                            target == context.sender() ? MessageConstants.COMMAND_HEAD_SUCCESS_SELF : MessageConstants.COMMAND_HEAD_SUCCESS,
                            Component.text(amount),
                            Component.text(source),
                            Component.text(target.getName())
                    );
                } else {
                    this.handleFeedback(context.sender(), MessageConstants.COMMAND_HEAD_CANCELLED);
                }
            }, () -> {}, target);
        });
    }

    @NotNull
    private TranslatableComponent failure(@NotNull Throwable error) {
        if (error instanceof TimeoutException || error instanceof HttpTimeoutException) {
            return MessageConstants.COMMAND_HEAD_TIMEOUT;
        }
        if (error instanceof CancellationException) {
            return MessageConstants.COMMAND_HEAD_CANCELLED;
        }
        if (error instanceof HeadFetchException fetch) {
            return switch (fetch.reason()) {
                case THROTTLED -> MessageConstants.COMMAND_HEAD_THROTTLED;
                case INVALID_INPUT -> MessageConstants.COMMAND_HEAD_INVALID;
                case INVALID_RESPONSE -> MessageConstants.COMMAND_HEAD_INVALID_RESPONSE;
                case SERVICE_ERROR -> MessageConstants.COMMAND_HEAD_FAILURE;
            };
        }
        return MessageConstants.COMMAND_HEAD_FAILURE;
    }

    @Override
    public String getFeatureID() {
        return "head";
    }
}