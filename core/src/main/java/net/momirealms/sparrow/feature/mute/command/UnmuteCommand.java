package net.momirealms.sparrow.feature.mute.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.mute.MuteFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class UnmuteCommand extends BukkitCommandFeature {
    private final MuteFeature feature;

    public UnmuteCommand(@NotNull MuteFeature feature) {
        super(SparrowPlugin.instance().commandManager(), SparrowPlugin.instance());
        this.feature = feature;
    }

    @Override
    public void registerCommand(@NotNull CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> cmd = builder.required("player", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerDirectory())).handler(this::execute);
        manager.command(cmd);
    }

    private void execute(CommandContext<CommandSender> context) {
        String input = context.get("player");
        this.feature.resolvePlayer(input).thenCompose(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(input));
                return CompletableFuture.completedFuture(null);
            }
            return this.feature.unmute(found.get().uuid(), context.sender().getName()).thenAccept(revoked -> this.handleFeedback(
                    context,
                    Component.translatable(revoked ? "command.unmute.success" : "command.unmute.not-muted"),
                    Component.text(found.get().name())
            ));
        }).whenComplete((ignored, error) -> {
            if (error != null) {
                this.plugin().logger().warn("Failed to unmute " + input, error);
                this.handleFeedback(context, MessageConstants.COMMAND_DATABASE_FAILED);
            }
        });
    }

    @Override
    public String getFeatureID() {
        return "unmute";
    }
}