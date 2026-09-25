package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.player.SparrowPlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

public final class MoreCommand extends BukkitCommandFeature {
    public MoreCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.optional("amount", IntegerParser.integerParser(1, 6400))
                .flag(manager.flagBuilder("player").withComponent(PlayerParser.playerParser()))
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.flags().getValue("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        int amount = context.getOrDefault("amount", 0);
        this.plugin().scheduler().platform().run(() -> {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir() || item.getAmount() <= 0) {
                this.handleFeedback(context, MessageConstants.COMMAND_MORE_NO_CHANGE, Component.text(player.getName()));
                return;
            }
            int maxStack = item.getType().getMaxStackSize();
            if (amount == 0) {
                int added = maxStack - item.getAmount();
                if (added <= 0) {
                    this.handleFeedback(context, MessageConstants.COMMAND_MORE_NO_CHANGE, Component.text(player.getName()));
                    return;
                }
                item.setAmount(maxStack);
                player.getInventory().setItemInMainHand(item);
                this.handleFeedback(context, MessageConstants.COMMAND_MORE_SUCCESS, Component.text(player.getName()), Component.text(added));
                return;
            }
            if (amount > maxStack * 100) {
                this.handleFeedback(context, MessageConstants.COMMAND_MORE_TOO_MANY, Component.text(maxStack * 100));
                return;
            }
            SparrowPlayer receiver = this.plugin().playerManager().getPlayer(player);
            int remaining = amount;
            while (remaining > 0) {
                int count = Math.min(maxStack, remaining);
                ItemStack copy = item.clone();
                copy.setAmount(count);
                receiver.dropItem(copy);
                remaining -= count;
            }
            this.handleFeedback(context, MessageConstants.COMMAND_MORE_SUCCESS, Component.text(player.getName()), Component.text(amount));
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "more";
    }
}
