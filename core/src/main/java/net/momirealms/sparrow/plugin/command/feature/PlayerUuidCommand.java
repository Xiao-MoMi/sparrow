package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

// 按玩家名查询 UUID, 只查集群名单和本插件数据库
public final class PlayerUuidCommand extends BukkitCommandFeature {

    public PlayerUuidCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerManager().cluster()))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String name = context.get("player");
        this.plugin().playerManager().resolvePlayer(name).thenAccept(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(sender, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(name));
                return;
            }
            String uuid = found.get().uuid().toString();
            this.handleFeedback(sender, MessageConstants.COMMAND_PLAYER_UUID_SUCCESS, Component.text(found.get().name()),
                    Component.text(uuid).hoverEvent(Component.text(uuid)).clickEvent(ClickEvent.copyToClipboard(uuid)));
        }).exceptionally(error -> {
            this.plugin().logger().warn("Failed to query the UUID of " + name, error);
            this.handleFeedback(sender, MessageConstants.COMMAND_DATABASE_FAILED);
            return null;
        });
    }

    @Override
    public String getFeatureID() {
        return "player-uuid";
    }
}
