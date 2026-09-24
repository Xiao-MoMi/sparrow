package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.data.ProtoItemStack;
import org.incendo.cloud.bukkit.parser.ItemStackParser;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class TotemAnimationCommand extends BukkitCommandFeature {
    public TotemAnimationCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.required("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .required("item", ItemStackParser.itemStackParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        MultiplePlayerSelector selector = context.get("targets");
        Collection<Player> players = selector.values();
        if (players.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }

        ProtoItemStack parsed = context.get("item");
        ItemStack item = parsed.createItemStack(1);
        if (item.getType() != Material.TOTEM_OF_UNDYING) {
            this.handleFeedback(context, MessageConstants.COMMAND_TOTEM_ANIMATION_INVALID);
            return;
        }
        for (Player player : players) {
            this.plugin().playerManager().getPlayer(player).sendTotemAnimation(item);
            this.handleFeedback(context, MessageConstants.COMMAND_TOTEM_ANIMATION_SUCCESS, Component.text(player.getName()));
        }
    }

    @Override
    public String getFeatureID() {
        return "totem-animation";
    }
}
