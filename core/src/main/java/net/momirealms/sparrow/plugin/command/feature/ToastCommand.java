package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.advancement.AdvancementFrame;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.util.Components;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.data.ProtoItemStack;
import org.incendo.cloud.bukkit.parser.ItemStackParser;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class ToastCommand extends BukkitCommandFeature {
    public ToastCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.required("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .required("type", EnumParser.enumParser(AdvancementFrame.class))
                .required("item", ItemStackParser.itemStackParser())
                .required("message", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .flag(manager.flagBuilder("legacy-color").withAliases("l"))
                .flag(manager.flagBuilder("parse").withAliases("p"))
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
        ItemStack icon = parsed.createItemStack(1);
        if (icon.getType().isAir()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TOAST_INVALID_ICON);
            return;
        }
        AdvancementFrame type = context.get("type");
        String message = context.get("message");
        PluginConfig.TextOptions text = PluginConfig.text();
        boolean legacy = text.parseLegacyColor() || context.flags().hasFlag("legacy-color");
        boolean placeholders = text.parsePlaceholder() || context.flags().hasFlag("parse");
        for (Player player : players) {
            SparrowPlayer receiver = this.plugin().playerManager().getPlayer(player);
            Component component = Components.miniMessage(placeholders ? this.plugin().compatibilityManager().parsePlaceholders(player, message) : message, legacy);
            receiver.sendToast(component, icon, type);
            this.handleFeedback(context, MessageConstants.COMMAND_TOAST_SUCCESS, Component.text(player.getName()));
        }
    }

    @Override
    public String getFeatureID() {
        return "toast";
    }
}
