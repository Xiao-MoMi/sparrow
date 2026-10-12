package net.momirealms.sparrow.feature.rtp;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ServerParser;
import net.momirealms.sparrow.plugin.command.parser.ServerWorldParser;
import net.momirealms.sparrow.teleport.TeleportDestination;
import net.momirealms.sparrow.teleport.TeleportType;
import net.momirealms.sparrow.teleport.search.RandomTeleportArea;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

public final class RtpCommand extends BukkitCommandFeature {

    public RtpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NotNull CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class)
                .required("server", ParserDescriptor.of(new ServerParser<>(server -> true, true), String.class))
                .required("world", ServerWorldParser.serverWorldParser("server"))
                .required("min-radius", DoubleParser.doubleParser(0))
                .required("max-radius", DoubleParser.doubleParser(0))
                .flag(manager.flagBuilder("center-x").withComponent(DoubleParser.doubleParser()).build())
                .flag(manager.flagBuilder("center-z").withComponent(DoubleParser.doubleParser()).build())
                .flag(manager.flagBuilder("min-y").withComponent(IntegerParser.integerParser()).build())
                .flag(manager.flagBuilder("max-y").withComponent(IntegerParser.integerParser()).build())
                .handler(this::execute));
    }

    private void execute(CommandContext<Player> context) {
        RandomTeleportArea area = new RandomTeleportArea(
                context.get("world"),
                context.flags().getValue("center-x", 0.0),
                context.flags().getValue("center-z", 0.0),
                context.get("min-radius"),
                context.get("max-radius"),
                context.flags().getValue("min-y", null),
                context.flags().getValue("max-y", null)
        );
        try {
            area.validate();
        } catch (IllegalArgumentException error) {
            this.handleFeedback(context, MessageConstants.COMMAND_RTP_INVALID_AREA);
            return;
        }
        Player player = context.sender();
        String server = context.get("server");
        this.plugin().teleportService()
                .teleport(player, TeleportType.RTP, server, new TeleportDestination.RTP(area), true)
                .thenAccept(result -> {
                    switch (result) {
                        case LOCAL_SUCCESS -> this.handleFeedback(context, MessageConstants.COMMAND_RTP_SUCCESS);
                        case SERVER_OFFLINE -> this.handleFeedback(context, MessageConstants.COMMAND_RTP_SERVER_OFFLINE, Component.text(server));
                        case INVALID -> this.handleFeedback(context, MessageConstants.COMMAND_RTP_INVALID);
                        case FAILED -> this.handleFeedback(context, MessageConstants.COMMAND_TELEPORT_FAILURE_SELF);
                        case REMOTE_SUCCESS, REJECTED -> {
                        }
                    }
                })
                .exceptionally(error -> {
                    Throwable cause = error instanceof CompletionException ? error.getCause() : error;
                    if (cause instanceof TimeoutException) {
                        this.handleFeedback(context, MessageConstants.COMMAND_RTP_TIMEOUT);
                    } else {
                        this.plugin().logger().warn("Failed to randomly teleport " + player.getName() + " to " + server + "/" + area.world(), cause);
                        this.handleFeedback(context, MessageConstants.COMMAND_TELEPORT_FAILURE_SELF);
                    }
                    return null;
                });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return RtpFeature.ID;
    }
}