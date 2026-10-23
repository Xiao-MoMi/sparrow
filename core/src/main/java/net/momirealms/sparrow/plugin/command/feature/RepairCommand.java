package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.message.RepairItemsRequest;
import net.momirealms.sparrow.player.message.RepairItemsResponse;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.GlobalPlayerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.EquipmentSlot;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class RepairCommand extends BukkitCommandFeature {

    public RepairCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", GlobalPlayerParser.clusterPlayerParser())
                .flag(manager.flagBuilder("slot").withComponent(EnumParser.enumParser(EquipmentSlot.class)))
                .flag(manager.flagBuilder("value").withComponent(IntegerParser.integerParser(0)))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        OfflineSparrowPlayer target = this.resolveOnlinePlayer(context, context.get("player"));
        if (target == null) return;
        EquipmentSlot slot = context.flags().getValue("slot", EquipmentSlot.HAND);
        RepairItemsRequest request = new RepairItemsRequest(target.uniqueId(), false, slot, context.flags().getValue("value", -1));
        CompletableFuture<RepairItemsResponse> resultFuture;
        if (target instanceof BukkitSparrowPlayer) {
            resultFuture = request.execute();
        } else {
            String server = target.getCurrentServerId();
            if (server == null) {
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_OFFLINE, Component.text(target.name()));
                return;
            }
            resultFuture = this.plugin().messageBrokerManager().broker()
                    .publishTwoWay(request, server)
                    .orTimeout(5, TimeUnit.SECONDS);
        }
        resultFuture.whenComplete((result, error) -> {
            if (error != null) {
                this.plugin().logger().warn("Failed to execute Repair for " + target.name(), error);
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUEST_FAILED, Component.text(target.name()));
                return;
            }
            switch (result.status()) {
                case OFFLINE -> this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_OFFLINE, Component.text(target.name()));
                case ITEMLESS -> this.handleFeedback(context, MessageConstants.COMMAND_REPAIR_ITEMLESS, Component.text(target.name()), Component.text(slot.name()));
                case NOT_DAMAGEABLE -> this.handleFeedback(context, MessageConstants.COMMAND_REPAIR_NOT_DAMAGEABLE, Component.text(target.name()), Component.text(slot.name()));
                case SUCCESS -> this.handleFeedback(
                        context,
                        MessageConstants.COMMAND_REPAIR_SUCCESS,
                        Component.text(target.name()),
                        Component.text(slot.name()),
                        Component.text(result.restored()),
                        Component.text(result.remaining()),
                        Component.text(result.maximum()),
                        Component.text(result.count())
                );
            }
        });
    }

    @Override
    public String getFeatureID() {
        return "repair";
    }
}