package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.feature.head.HeadData;
import net.momirealms.sparrow.feature.head.HeadFeature;
import net.momirealms.sparrow.feature.head.HeadFetchException;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.OptionalWordParser;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Optional;
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
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.optional("source", OptionalWordParser.optionalWordParser(), (context, input) -> CompletableFuture.completedFuture(
                        this.plugin().playerManager().getOnlinePlayers().stream().map(player -> Suggestion.suggestion(player.name())).toList()))
                .flag(manager.flagBuilder("player").withComponent(MultiplePlayerSelectorParser.multiplePlayerSelectorParser()))
                .flag(manager.flagBuilder("amount").withComponent(IntegerParser.integerParser(1, 6400)))
                .flag(manager.flagBuilder("force"))
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(context -> this.plugin().scheduler().executeAsync(() -> this.execute(context)));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        SparrowPlugin plugin = this.plugin();
        HeadFeature feature = plugin.featureManager().feature(HeadFeature.ID, HeadFeature.class);
        if (!feature.enabled()) {
            this.handleFeedback(context.sender(), MessageConstants.COMMAND_FEATURE_DISABLED, Component.text(HeadFeature.ID));
            return;
        }
        String source = context.<Optional<String>>getOrDefault("source", Optional.empty()).orElse(context.sender() instanceof Player player ? player.getName() : null);
        if (source == null) {
            this.handleFeedback(context.sender(), MessageConstants.COMMAND_HEAD_SOURCE_REQUIRED);
            return;
        }
        MultiplePlayerSelector selector = context.flags().getValue("player", null);
        List<Player> targets = selector != null ? List.copyOf(selector.values()) : context.sender() instanceof Player player ? List.of(player) : List.of();
        if (targets.isEmpty()) {
            this.handleFeedback(context.sender(), selector != null ? MessageConstants.COMMAND_TARGETS_EMPTY : MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        BukkitSparrowPlayer sender = context.sender() instanceof Player player ? plugin.playerManager().getPlayer(player) : null;
        long generation = feature.generation();
        int amount = context.flags().getValue("amount", 1);
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
        future.whenCompleteAsync((data, error) -> {
            if (context.sender() instanceof Player player && (sender == null || plugin.playerManager().getPlayer(player) != sender)) return;
            if (error != null) {
                Throwable cause = error;
                while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                TranslatableComponent.Builder message = failure(cause);
                if (message == MessageConstants.COMMAND_HEAD_FAILURE || message == MessageConstants.COMMAND_HEAD_INVALID_RESPONSE) {
                    plugin.logger().warn("Failed to fetch a head", cause);
                }
                // 超时和失败始终反馈; --silent 只隐藏成功提示.
                this.handleFeedback(context.sender(), message, Component.text(source));
                return;
            }
            if (!feature.enabled() || feature.generation() != generation) {
                this.handleFeedback(context.sender(), MessageConstants.COMMAND_HEAD_CANCELLED);
                return;
            }
            if (data == null) {
                this.handleFeedback(context.sender(), MessageConstants.COMMAND_HEAD_NOT_FOUND, Component.text(source));
                return;
            }
            for (int i = 0; i < targets.size(); i++) {
                Player target = targets.get(i);
                plugin.scheduler().platform().run(() -> {
                    if (feature.give(target, data, amount, generation)) {
                        this.handleFeedback(context, MessageConstants.COMMAND_HEAD_SUCCESS, Component.text(amount), Component.text(source), Component.text(target.getName()));
                    } else {
                        this.handleFeedback(context.sender(), MessageConstants.COMMAND_HEAD_CANCELLED);
                    }
                }, () -> {}, target);
            }
        }, plugin.scheduler().async());
    }

    @NotNull
    private static TranslatableComponent.Builder failure(@NotNull Throwable error) {
        if (error instanceof TimeoutException || error instanceof HttpTimeoutException) return MessageConstants.COMMAND_HEAD_TIMEOUT;
        if (error instanceof CancellationException) return MessageConstants.COMMAND_HEAD_CANCELLED;
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
    public String getFeatureID() { return "head"; }
}
