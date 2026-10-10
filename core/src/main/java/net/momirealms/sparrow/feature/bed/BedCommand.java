package net.momirealms.sparrow.feature.bed;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.teleport.TeleportOptions;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public final class BedCommand extends BukkitCommandFeature {
    private final BedFeature feature;

    public BedCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull BedFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", PlayerParser.playerParser())
                .permission(this.otherPermission(builder))
                .handler(this::execute));
        manager.command(builder.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        this.teleport(context, player, player.getRespawnLocation());
    }

    // 床被拆除或被挡住时重生点为 null
    private void teleport(CommandContext<CommandSender> context, Player player, @Nullable Location destination) {
        if (destination == null) {
            this.handleFeedback(
                    context,
                    (player == context.sender() ? MessageConstants.COMMAND_BED_MISSING_SELF : MessageConstants.COMMAND_BED_MISSING),
                    Component.text(player.getName())
            );
            return;
        }
        String name = player.getName();
        boolean self = player == context.sender();
        TeleportOptions options = this.feature.config().teleportOptions().resolve(player, self);
        this.plugin().teleportService()
                .teleport(player, ServerConfig.serverId(), WorldLocation.from(destination), options)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        this.plugin().logger().warn("Failed to teleport " + name + " to the bed", error);
                    }
                    // 冷却和预热取消的原因由传送服务提示.
                    if (result == TeleportResult.COOLDOWN || result == TeleportResult.CANCELLED) {
                        return;
                    }
                    this.handleFeedback(
                            context,
                            error == null && result == TeleportResult.LOCAL_SUCCESS
                                    ? (self ? MessageConstants.COMMAND_BED_SUCCESS_SELF : MessageConstants.COMMAND_BED_SUCCESS)
                                            : (self ? MessageConstants.COMMAND_TELEPORT_FAILURE_SELF : MessageConstants.COMMAND_TELEPORT_FAILURE),
                            Component.text(name)
                    );
                });
    }

    @Override
    public String getFeatureID() {
        return BedFeature.ID;
    }
}