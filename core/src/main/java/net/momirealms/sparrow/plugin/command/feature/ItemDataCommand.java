package net.momirealms.sparrow.plugin.command.feature;

import com.mojang.serialization.DataResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.minecraft.world.item.ItemStack;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.DataTreeRenderer;
import net.momirealms.sparrow.util.ItemUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class ItemDataCommand extends BukkitCommandFeature {
    private static final int MAX_CHAT_LINES = 10;

    public ItemDataCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.senderType(Player.class)
                .flag(manager.flagBuilder("full").build())
                .flag(manager.flagBuilder("chat").build())
                .handler(this::execute);
    }

    private void execute(CommandContext<Player> context) {
        Player player = context.sender();
        this.plugin().scheduler().platform().run(() -> {
            ItemStack item = this.plugin().playerManager().getPlayer(player).getItemInMainHand();
            if (item.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_ITEM_DATA_ITEMLESS);
                return;
            }
            DataResult<Map<String, Object>> result = ItemUtils.readableData(item, ((CraftServer) player.getServer()).getServer().registryAccess(), context.flags().hasFlag("full"));
            if (result.error().isPresent()) {
                this.plugin().logger().warn("Failed to read item data: " + result.error().get().message());
                this.handleFeedback(context, MessageConstants.COMMAND_ITEM_DATA_FAILURE);
                return;
            }
            Map<String, Object> data = result.getOrThrow();
            List<Component> lines = DataTreeRenderer.render(data, true);
            Component output;
            if (!context.flags().hasFlag("chat") && lines.size() > MAX_CHAT_LINES) {
                Component hover = Component.join(JoinConfiguration.newlines(), DataTreeRenderer.render(data, false));
                output = MessageConstants.COMMAND_ITEM_DATA_SUMMARY.build().arguments(Component.text(lines.size())).hoverEvent(hover);
            } else {
                output = Component.join(JoinConfiguration.newlines(), lines);
            }
            this.handleFeedback(context, MessageConstants.COMMAND_ITEM_DATA_SUCCESS, output);
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "item-data";
    }
}
