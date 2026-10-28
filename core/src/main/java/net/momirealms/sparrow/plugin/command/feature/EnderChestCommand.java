package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.OpenContainerMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class EnderChestCommand extends BukkitCommandFeature {

    public EnderChestCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", GlobalPlayerParser.clusterPlayerParser())
                .flag(manager.flagBuilder("title").withComponent(StringParser.greedyFlagYieldingStringParser()))
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .permission(this.otherPermission(builder))
                .handler(this::execute));
        manager.command(builder.handler(this::execute));
        manager.command(builder.literal("--title")
                .required("title", StringParser.greedyFlagYieldingStringParser())
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String input = context.getOrDefault("player", context.sender() instanceof Player sender ? sender.getUniqueId().toString() : null);
        if (input == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        OfflineSparrowPlayer target = this.resolveOnlinePlayer(context, input);
        if (target == null) return;
        String title = context.getOrDefault("title", context.flags().getValue("title", null));
        if (!(target instanceof BukkitSparrowPlayer local)) {
            this.sendRemoteRequest(
                    context,
                    target,
                    new OpenContainerMessage(target.uniqueId(), OpenContainerMessage.ContainerType.ENDER_CHEST, title)
            );
            return;
        }
        Player player = local.platformPlayer;
        this.plugin().scheduler().platform().run(
                () -> {
                    if (!OpenContainerMessage.ContainerType.ENDER_CHEST.open(player, title)) {
                        this.handleFeedback(
                                context,
                                (player == context.sender() ? MessageConstants.COMMAND_ENDER_CHEST_FAILED_SELF : MessageConstants.COMMAND_ENDER_CHEST_FAILED),
                                Component.text(player.getName())
                        );
                        return;
                    }
                    this.handleFeedback(
                            context,
                            (player == context.sender() ? MessageConstants.COMMAND_ENDER_CHEST_SUCCESS_SELF : MessageConstants.COMMAND_ENDER_CHEST_SUCCESS),
                            Component.text(player.getName())
                    );
                },
                () -> {},
                player
        );
    }

    @Override
    public String getFeatureID() {
        return "ender_chest";
    }
}