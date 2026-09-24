package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

public final class HealCommand extends BukkitCommandFeature {
    public HealCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.optional("player", PlayerParser.playerParser())
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        this.plugin().scheduler().platform().run(() -> {
            if (player.isDead()) {
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_DEAD, Component.text(player.getName()));
                return;
            }
            player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
            player.setFoodLevel(20);
            player.setSaturation(10.0f);
            this.handleFeedback(context, MessageConstants.COMMAND_HEAL_SUCCESS, Component.text(player.getName()));
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "heal";
    }
}

