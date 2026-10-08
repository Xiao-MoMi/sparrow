package net.momirealms.sparrow.feature.mute.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.mute.MuteFeature;
import net.momirealms.sparrow.feature.mute.MuteRecord;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import net.momirealms.sparrow.plugin.command.parser.DurationParser;
import net.momirealms.sparrow.util.DurationUtils;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class MuteCommand extends BukkitCommandFeature {
    private final MuteFeature feature;

    public MuteCommand(@NotNull MuteFeature feature) {
        super(SparrowPlugin.instance().commandManager(), SparrowPlugin.instance());
        this.feature = feature;
    }

    @Override
    public void registerCommand(@NotNull CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerManager().cluster()))
                .required("time", DurationParser.durationParser())
                .optional("reason", StringParser.greedyStringParser())
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String input = context.get("player");
        Duration time = context.get("time");
        String reason = context.getOrDefault("reason", "");
        if (reason.codePointCount(0, reason.length()) > MuteRecord.MAX_REASON_LENGTH) {
            this.handleFeedback(context, Component.translatable("command.mute.reason-too-long"), Component.text(MuteRecord.MAX_REASON_LENGTH));
            return;
        }
        if (time.toMillis() > Long.MAX_VALUE - System.currentTimeMillis()) {
            this.handleFeedback(context, Component.translatable("command.mute.time-too-long"));
            return;
        }
        this.feature.resolvePlayer(input).thenCompose(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(input));
                return CompletableFuture.completedFuture(null);
            }
            return this.feature.mute(found.get(), time, reason, context.sender().getName()).thenAccept(created -> this.handleFeedback(
                    context,
                    Component.translatable(created ? "command.mute.success" : "command.mute.already-muted"),
                    Component.text(found.get().name()),
                    Component.text(DurationUtils.format(time.toMillis()))
            ));
        }).whenComplete((ignored, error) -> {
            if (error != null) {
                this.plugin().logger().warn("Failed to mute " + input, error);
                this.handleFeedback(context, MessageConstants.COMMAND_DATABASE_FAILED);
            }
        });
    }

    @Override
    public String getFeatureID() {
        return "mute";
    }
}