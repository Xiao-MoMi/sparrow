package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.SendTotemAnimationMessage;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BatchCommandFeedback;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.ProtoItemStack;
import org.incendo.cloud.bukkit.parser.ItemStackParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class TotemAnimationCommand extends BukkitCommandFeature {

    public TotemAnimationCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("targets", GlobalPlayerParser.clusterPlayersParser())
                .required("item", ItemStackParser.itemStackParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        ProtoItemStack parsed = context.get("item");
        ItemStack item = parsed.createItemStack(1);
        List<String> targets = context.get("targets");
        BatchCommandFeedback feedback = new BatchCommandFeedback(this, context, targets.size());
        for (String input : targets) {
            OfflineSparrowPlayer target = this.resolveOnlinePlayer(context, input, feedback);
            if (target == null) continue;
            if (!(target instanceof BukkitSparrowPlayer local)) {
                this.sendRemoteRequest(context, target, new SendTotemAnimationMessage(target.uniqueId(), item), feedback);
                continue;
            }
            if (!local.sendTotemAnimation(item)) {
                feedback.failure(MessageConstants.COMMAND_PLAYER_OFFLINE, Component.text(target.name()));
                continue;
            }
            feedback.success(
                    local.platformPlayer == context.sender() ? MessageConstants.COMMAND_TOTEM_ANIMATION_SUCCESS_SELF : MessageConstants.COMMAND_TOTEM_ANIMATION_SUCCESS,
                    Component.text(target.name())
            );
        }
    }

    @Override
    public String getFeatureID() {
        return "totem_animation";
    }
}