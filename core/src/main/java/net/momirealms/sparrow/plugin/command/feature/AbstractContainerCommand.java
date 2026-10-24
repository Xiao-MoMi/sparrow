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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

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
        String input = context.getOrDefault("player", context.sender() instanceof Player sender ? sender.getUniqueId().toString() : null);
        if (input == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        OfflineSparrowPlayer target = this.resolveOnlinePlayer(context, input, MessageConstants.COMMAND_CONTAINER_OFFLINE);
        if (target == null) return;
        if (target instanceof BukkitSparrowPlayer local) {
            this.openLocal(context, local);
            return;
        }
        this.sendRemoteRequest(
                context,
                target,
                new OpenContainerMessage(target.uniqueId(), this.type),
                MessageConstants.COMMAND_CONTAINER_OFFLINE,
                MessageConstants.COMMAND_CONTAINER_REQUEST_SENT
        );
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