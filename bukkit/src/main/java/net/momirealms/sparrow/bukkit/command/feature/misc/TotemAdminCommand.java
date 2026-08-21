package net.momirealms.sparrow.bukkit.command.feature.misc;

import net.momirealms.sparrow.bukkit.SparrowNMSProxy;
import net.momirealms.sparrow.bukkit.command.BukkitCommandFeature;
import net.momirealms.sparrow.bukkit.command.key.SparrowBukkitArgumentKeys;
import net.momirealms.sparrow.common.command.SparrowCommandManager;
import net.momirealms.sparrow.common.command.key.SparrowFlagKeys;
import net.momirealms.sparrow.common.command.key.SparrowMetaKeys;
import net.momirealms.sparrow.common.locale.MessageConstants;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.data.ProtoItemStack;
import org.incendo.cloud.bukkit.parser.ItemStackParser;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;

public class TotemAdminCommand extends BukkitCommandFeature<CommandSender> {

    public TotemAdminCommand(SparrowCommandManager<CommandSender> sparrowCommandManager) {
        super(sparrowCommandManager);
    }

    @Override
    public String getFeatureID() {
        return "totemanimation_admin";
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .required(SparrowBukkitArgumentKeys.PLAYER_SELECTOR, MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .meta(SparrowMetaKeys.ALLOW_EMPTY_PLAYER_SELECTOR, false)
                .required("item", ItemStackParser.itemStackParser())
                .flag(SparrowFlagKeys.SILENT_FLAG)
                .handler(commandContext -> {
                    MultiplePlayerSelector selector = commandContext.get(SparrowBukkitArgumentKeys.PLAYER_SELECTOR);
                    var players = selector.values();
                    ProtoItemStack itemStack = commandContext.get("item");
                    ItemStack bukkitStack = itemStack.createItemStack(1);
                    if (bukkitStack.getType() != Material.TOTEM_OF_UNDYING) {
                        handleFeedback(commandContext, MessageConstants.COMMANDS_ADMIN_TOTEM_FAILED);
                        return;
                    }
                    for (Player player : players) {
                        SparrowNMSProxy.getInstance().sendTotemAnimation(
                                player,
                                bukkitStack
                        );
                    }
                    var pair = resolveSelector(selector, MessageConstants.COMMANDS_ADMIN_TOTEM_SUCCESS_SINGLE, MessageConstants.COMMANDS_ADMIN_TOTEM_SUCCESS_MULTIPLE);
                    handleFeedback(commandContext, pair.left(), pair.right());
                });
    }
}
