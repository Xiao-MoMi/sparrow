package net.momirealms.sparrow.plugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.util.ThrowableUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BatchCommandFeedback {
    private final BukkitCommandFeature feature;
    private final CommandContext<?> context;
    private final int total;
    private final boolean silent;
    private final Map<String, List<Component>> details = new LinkedHashMap<>();
    private int remaining;
    private int succeeded;
    private int submitted;
    private int failed;
    private int visibleFailures;

    public BatchCommandFeedback(@NotNull BukkitCommandFeature feature, @NotNull CommandContext<?> context, int total) {
        this.feature = feature;
        this.context = context;
        this.total = total;
        this.remaining = total;
        this.silent = context.flags().hasFlag("silent");
    }

    public void success(@NotNull TranslatableComponent message, @NotNull Component... arguments) {
        this.complete(Outcome.SUCCESS, message, arguments, false, true);
    }

    public void submitted(@NotNull TranslatableComponent message, @NotNull Component... arguments) {
        this.complete(Outcome.SUBMITTED, message, arguments, false, true);
    }

    public void failure(@NotNull TranslatableComponent message, @NotNull Component... arguments) {
        this.complete(Outcome.FAILURE, message, arguments, false, true);
    }

    public void failureAlways(@NotNull TranslatableComponent message, @NotNull Component... arguments) {
        this.complete(Outcome.FAILURE, message, arguments, true, true);
    }

    public void failureFor(@NotNull String name, @NotNull TranslatableComponent message, @NotNull Component... arguments) {
        if (this.total == 1) {
            this.failure(message, arguments);
        } else {
            this.failure(MessageConstants.COMMAND_BATCH_DETAIL, Component.text(name), message.arguments(arguments));
        }
    }

    public void result(boolean success, @NotNull TranslatableComponent message, @NotNull Component... arguments) {
        this.complete(success ? Outcome.SUCCESS : Outcome.FAILURE, message, arguments, false, true);
    }

    public void successWithoutMessage() {
        this.complete(Outcome.SUCCESS, null, new Component[0], false, false);
    }

    public void progress(@NotNull TranslatableComponent message, @NotNull Component... arguments) {
        if (this.total == 1) this.feature.handleFeedback(this.context, message, arguments);
    }

    public void unavailable(@NotNull String name) {
        this.complete(Outcome.FAILURE, MessageConstants.COMMAND_BATCH_UNAVAILABLE, new Component[]{Component.text(name)}, false, false);
    }

    public void retired(@NotNull String name) {
        if (this.total == 1) return;
        this.feature.plugin().scheduler().async().execute(() -> this.unavailable(name));
    }

    public void error(@NotNull String name, @NotNull Throwable error) {
        this.feature.plugin().logger().warn("Failed to execute " + this.feature.getFeatureID() + " for " + name, ThrowableUtils.unwrap(error));
        this.complete(Outcome.FAILURE, MessageConstants.COMMAND_BATCH_ERROR, new Component[]{Component.text(name)}, false, false);
    }

    public synchronized void abort(@NotNull Throwable error) {
        this.feature.plugin().logger().warn("Failed to execute " + this.feature.getFeatureID(), ThrowableUtils.unwrap(error));
        if (this.total == 1) return;
        int count = this.remaining;
        for (int i = 0; i < count; i++) {
            this.complete(Outcome.FAILURE, MessageConstants.COMMAND_BATCH_ERROR, new Component[]{Component.text(this.feature.getFeatureID())}, false, false);
        }
    }

    private synchronized void complete(Outcome outcome, @Nullable TranslatableComponent message, Component[] arguments, boolean always, boolean reportSingle) {
        if (this.total == 1) {
            if (!reportSingle) return;
            if (always) {
                this.feature.handleFeedback((CommandSender) this.context.sender(), message, arguments);
            } else {
                this.feature.handleFeedback(this.context, message, arguments);
            }
            return;
        }
        switch (outcome) {
            case SUCCESS -> this.succeeded++;
            case SUBMITTED -> this.submitted++;
            case FAILURE -> {
                this.failed++;
                if (!this.silent || always) {
                    this.visibleFailures++;
                    this.details.computeIfAbsent(message.key(), ignored -> new ArrayList<>()).add(message.arguments(arguments));
                }
            }
        }
        if (--this.remaining != 0) return;
        if (this.silent && this.visibleFailures == 0) return;
        List<Component> lines = new ArrayList<>(this.visibleFailures);
        for (List<Component> group : this.details.values()) {
            lines.addAll(group);
        }
        TranslatableComponent summary = this.silent ? MessageConstants.COMMAND_BATCH_FAILURES : MessageConstants.COMMAND_BATCH_SUMMARY;
        if (!lines.isEmpty()) {
            summary = summary.hoverEvent(Component.join(JoinConfiguration.newlines(), lines));
        }
        if (this.silent) {
            this.sendSummary(summary, Component.text(this.feature.getFeatureID()), Component.text(this.visibleFailures));
        } else {
            this.sendSummary(
                    summary,
                    Component.text(this.feature.getFeatureID()),
                    Component.text(this.total),
                    Component.text(this.succeeded),
                    Component.text(this.submitted),
                    Component.text(this.failed)
            );
        }
    }

    private void sendSummary(TranslatableComponent message, Component... arguments) {
        CommandSender sender = (CommandSender) this.context.sender();
        if (sender instanceof Player player && this.feature.plugin().playerManager().getOnlinePlayer(player) == null) {
            SparrowPlayer recipient = this.feature.plugin().playerManager().getGlobalOnlinePlayer(player.getUniqueId());
            if (recipient != null) recipient.sendTranslatedMessage(message.arguments(arguments));
            return;
        }
        this.feature.handleFeedback(sender, message, arguments);
    }

    private enum Outcome {
        SUCCESS, SUBMITTED, FAILURE
    }
}