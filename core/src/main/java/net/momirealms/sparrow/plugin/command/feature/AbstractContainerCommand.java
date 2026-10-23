package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.OpenContainerMessage;
import net.momirealms.sparrow.player.message.OpenContainerMessage.ContainerType;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

abstract class AbstractContainerCommand extends BukkitCommandFeature {
    private final String id;
    private final ContainerType type;
    private final TranslatableComponent successSelf;
    private final TranslatableComponent successOther;

    AbstractContainerCommand(
            @NotNull CommandManager manager,
            @NotNull SparrowPlugin plugin,
            @NotNull String id,
            @NotNull ContainerType type,
            @NotNull TranslatableComponent successSelf,
            @NotNull TranslatableComponent successOther
    ) {
        super(manager, plugin);
        this.id = id;
        this.type = type;
        this.successSelf = successSelf;
        this.successOther = successOther;
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", GlobalPlayerParser.clusterPlayerParser())
                .permission(this.otherPermission(builder))
                .handler(this::execute));
        manager.command(builder.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String input = context.getOrDefault("player", null);
        OfflineSparrowPlayer target;
        if (input == null) {
            if (!(context.sender() instanceof Player player)) {
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
                return;
            }
            input = player.getName();
            target = this.plugin().playerManager().getOnlinePlayer(player);
        } else {
            UUID uuid = UUIDUtils.parse(input);
            target = uuid == null ? this.plugin().playerManager().getGlobalOnlinePlayer(input) : this.plugin().playerManager().getGlobalOnlinePlayer(uuid);
        }
        if (target == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_CONTAINER_OFFLINE, Component.text(input));
            return;
        }
        if (target instanceof BukkitSparrowPlayer local) {
            this.openLocal(context, local);
            return;
        }
        String server = target.getCurrentServerId();
        if (server == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_CONTAINER_OFFLINE, Component.text(target.name()));
            return;
        }
        String name = target.name();
        this.plugin().messageBrokerManager()
                .publishOneWay(new OpenContainerMessage(target.uniqueId(), this.type), server)
                .whenComplete((ignored, error) -> {
                    if (error == null) {
                        this.handleFeedback(context, MessageConstants.COMMAND_CONTAINER_REQUEST_SENT, Component.text(name));
                    } else {
                        this.plugin().logger().warn("Failed to send container request for " + name, error);
                        this.handleFeedback(context, MessageConstants.COMMAND_CONTAINER_REQUEST_FAILED, Component.text(name));
                    }
                });
    }

    private void openLocal(CommandContext<CommandSender> context, BukkitSparrowPlayer player) {
        PlatformExecutor executor = this.plugin().scheduler().platform();
        Runnable open = () -> {
            if (!player.isOnlineLocally()) {
                this.handleFeedback(context, MessageConstants.COMMAND_CONTAINER_OFFLINE, Component.text(player.name()));
                return;
            }
            this.type.open(player.platformPlayer);
            this.handleFeedback(
                    context,
                    player.platformPlayer == context.sender() ? this.successSelf : this.successOther,
                    Component.text(player.name())
            );
        };
        if (executor.isOwnedByCurrentRegion(player.platformPlayer)) {
            open.run();
        } else {
            executor.run(open, () -> {}, player.platformPlayer);
        }
    }

    @Override
    public String getFeatureID() {
        return this.id;
    }
}