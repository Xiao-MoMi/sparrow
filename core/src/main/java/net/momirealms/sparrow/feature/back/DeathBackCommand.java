package net.momirealms.sparrow.feature.back;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.jetbrains.annotations.NotNull;

public final class DeathBackCommand extends BukkitCommandFeature {

    public DeathBackCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        Permission otherPermission = this.otherPermission(builder);
        CommandFlag<Void> silent = manager.flagBuilder("silent").withAliases("s").build();
        manager.command(builder.handler(this::execute));
        // 自用的静默入口使用字面量, 与玩家动态节点在命令树中明确区分
        manager.command(builder
                .literal("--silent", "-s")
                .handler(context -> {
                    context.flags().addPresenceFlag(silent);
                    this.execute(context);
                }));
        manager.command(builder
                .required("player", PlayerParser.playerParser())
                .permission(otherPermission)
                .flag(silent)
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        BukkitSparrowPlayer traveler = this.plugin().playerManager().getOnlinePlayer(player);
        if (traveler == null) {
            this.handleFeedback(context, MessageConstants.TELEPORT_UNAVAILABLE);
            return;
        }
        boolean self = player == context.sender();
        this.plugin().featureManager().feature(BackFeature.ID, BackFeature.class).deathback(traveler).thenAccept(reason -> {
            if (reason == null) {
                if (self) {
                    if (!context.flags().hasFlag("silent")) traveler.sendMessage(MessageConstants.COMMAND_DEATH_BACK_SUCCESS_SELF);
                } else {
                    this.handleFeedback(context, MessageConstants.COMMAND_DEATH_BACK_SUCCESS, Component.text(player.getName()));
                }
            } else if (!self) {
                this.handleFeedback(context, MessageConstants.COMMAND_DEATH_BACK_FAILED, Component.text(player.getName()), reason);
            }
        });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "death-back";
    }
}