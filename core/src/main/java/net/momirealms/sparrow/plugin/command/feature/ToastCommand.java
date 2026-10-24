package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.minecraft.advancements.AdvancementType;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.SendToastMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.ProtoItemStack;
import org.incendo.cloud.bukkit.parser.ItemStackParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public final class ToastCommand extends BukkitCommandFeature {

    public ToastCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("targets", GlobalPlayerParser.clusterPlayersParser())
                .required("type", EnumParser.enumParser(AdvancementType.class))
                .required("item", ItemStackParser.itemStackParser())
                .required("message", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String message = context.get("message");
        ProtoItemStack parsed = context.get("item");
        ItemStack icon = parsed.createItemStack(1);
        if (icon.getType().isAir()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TOAST_INVALID_ICON);
            return;
        }
        AdvancementType type = context.get("type");
        List<String> targets = context.get("targets");
        for (String input : targets) {
            OfflineSparrowPlayer target = this.resolveOnlinePlayer(context, input);
            if (target == null) continue;
            if (!(target instanceof BukkitSparrowPlayer local)) {
                this.sendRemoteRequest(context, target, new SendToastMessage(target.uniqueId(), message, icon, type));
                continue;
            }
            if (!local.isOnlineLocally()) {
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_OFFLINE, Component.text(local.name()));
                continue;
            }
            local.sendToast(message, icon, type);
            this.handleFeedback(
                    context,
                    local.platformPlayer == context.sender() ? MessageConstants.COMMAND_TOAST_SUCCESS_SELF : MessageConstants.COMMAND_TOAST_SUCCESS,
                    Component.text(local.name())
            );
        }
    }

    @Override
    public String getFeatureID() {
        return "toast";
    }
}