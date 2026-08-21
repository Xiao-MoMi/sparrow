package net.momirealms.sparrow.bukkit.command.feature.message;

import net.momirealms.sparrow.bukkit.SparrowNMSProxy;
import net.momirealms.sparrow.bukkit.command.BukkitCommandFeature;
import net.momirealms.sparrow.bukkit.command.key.SparrowBukkitArgumentKeys;
import net.momirealms.sparrow.bukkit.util.PlaceholderUtils;
import net.momirealms.sparrow.common.command.SparrowCommandManager;
import net.momirealms.sparrow.common.command.key.SparrowFlagKeys;
import net.momirealms.sparrow.common.feature.toast.AdvancementType;
import net.momirealms.sparrow.common.helper.AdventureHelper;
import net.momirealms.sparrow.common.locale.MessageConstants;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.data.ProtoItemStack;
import org.incendo.cloud.bukkit.parser.ItemStackParser;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.StringParser;

public class ToastAdminCommand extends BukkitCommandFeature<CommandSender> {

    public ToastAdminCommand(SparrowCommandManager<CommandSender> sparrowCommandManager) {
        super(sparrowCommandManager);
    }

    @Override
    public String getFeatureID() {
        return "toast_admin";
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder
                .required(SparrowBukkitArgumentKeys.PLAYER_SELECTOR, MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .required("type", EnumParser.enumParser(AdvancementType.class))
                .required("item", ItemStackParser.itemStackParser())
                .required("message", StringParser.greedyFlagYieldingStringParser())
                .flag(SparrowFlagKeys.SILENT_FLAG)
                .flag(SparrowFlagKeys.LEGACY_COLOR_FLAG)
                .flag(SparrowFlagKeys.PARSE_FLAG)
                .handler(commandContext -> {
                    MultiplePlayerSelector selector = commandContext.get(SparrowBukkitArgumentKeys.PLAYER_SELECTOR);
                    boolean legacy = commandContext.flags().hasFlag(SparrowFlagKeys.LEGACY_COLOR_FLAG);
                    var players = selector.values();
                    ProtoItemStack itemStack = commandContext.get("item");
                    ItemStack bukkitStack = itemStack.createItemStack(1);
                    String message = commandContext.get("message");
                    boolean parse = commandContext.flags().hasFlag(SparrowFlagKeys.PARSE_FLAG);
                    AdvancementType type = commandContext.get("type");
                    for (Player player : players) {
                        String temp;
                        if (parse) temp = PlaceholderUtils.parse(player, message);
                        else temp = message;
                        SparrowNMSProxy.getInstance().sendToast(
                                player,
                                bukkitStack,
                                AdventureHelper.componentToJson(
                                        AdventureHelper.getMiniMessage().deserialize(legacy ? AdventureHelper.legacyToMiniMessage(temp) : temp)
                                ),
                                type
                        );
                    }
                    var pair = resolveSelector(selector, MessageConstants.COMMANDS_ADMIN_TOAST_SUCCESS_SINGLE, MessageConstants.COMMANDS_ADMIN_TOAST_SUCCESS_MULTIPLE);
                    handleFeedback(commandContext, pair.left(), pair.right());
                });
    }
}
