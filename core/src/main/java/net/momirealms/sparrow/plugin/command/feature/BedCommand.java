package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.util.EntityUtils;
import net.momirealms.sparrow.util.VersionHelper;
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
    public BedCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
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
        PlatformExecutor platform = this.plugin().scheduler().platform();
        platform.run(() -> {
            // 读取重生点要检查床所在的方块, Folia 上床可能在其他区域, 需要到床所在的区域线程读取
            if (!VersionHelper.hasFoliaPatch) {
                this.teleport(context, player, player.getRespawnLocation());
                return;
            }
            Location bed = player.getPotentialRespawnLocation();
            if (bed == null) {
                this.handleFeedback(context, (player == context.sender() ? MessageConstants.COMMAND_BED_MISSING_SELF : MessageConstants.COMMAND_BED_MISSING), Component.text(player.getName()));
                return;
            }
            platform.run(() -> {
                Location destination = player.getRespawnLocation();
                platform.run(() -> this.teleport(context, player, destination), () -> {}, player);
            }, bed.getWorld(), bed.getBlockX() >> 4, bed.getBlockZ() >> 4);
        }, () -> {}, player);
    }

    // 床被拆除或被挡住时重生点为 null
    private void teleport(CommandContext<CommandSender> context, Player player, @Nullable Location destination) {
        if (destination == null) {
            this.handleFeedback(context, (player == context.sender() ? MessageConstants.COMMAND_BED_MISSING_SELF : MessageConstants.COMMAND_BED_MISSING), Component.text(player.getName()));
            return;
        }
        String name = player.getName();
        EntityUtils.teleport(player, destination).whenComplete((success, error) -> {
            if (error != null) {
                this.plugin().logger().warn("Failed to teleport " + name + " to the bed", error);
            }
            this.handleFeedback(context, error == null && success ? (player == context.sender() ? MessageConstants.COMMAND_BED_SUCCESS_SELF : MessageConstants.COMMAND_BED_SUCCESS) : (player == context.sender() ? MessageConstants.COMMAND_TELEPORT_FAILURE_SELF : MessageConstants.COMMAND_TELEPORT_FAILURE), Component.text(name));
        });
    }

    @Override
    public String getFeatureID() {
        return "bed";
    }
}
