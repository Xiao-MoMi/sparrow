package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class HatCommand extends BukkitCommandFeature {
    public HatCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", PlayerParser.playerParser())
                .permission(this.otherPermission(builder))
                .handler(this::execute));
        manager.command(builder.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        this.plugin().scheduler().platform().run(() -> {
            SparrowPlayer receiver = this.plugin().playerManager().getPlayer(player);
            ServerPlayer handle = receiver.nmsPlayer();
            ItemStack hand = receiver.getItemInMainHand();
            if (hand.isEmpty()) {
                this.handleFeedback(context, (player == context.sender() ? MessageConstants.COMMAND_HAT_ITEMLESS_SELF : MessageConstants.COMMAND_HAT_ITEMLESS), Component.text(player.getName()));
                return;
            }
            // 和原版一样, 非创造模式下摘不下带绑定诅咒的头盔
            ItemStack helmet = handle.getItemBySlot(EquipmentSlot.HEAD);
            if (!helmet.isEmpty() && !handle.isCreative() && EnchantmentHelper.has(helmet, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
                this.handleFeedback(context, (player == context.sender() ? MessageConstants.COMMAND_HAT_BOUND_SELF : MessageConstants.COMMAND_HAT_BOUND), Component.text(player.getName()));
                return;
            }
            // 头上只戴一个, 其余留在主手
            handle.setItemSlot(EquipmentSlot.HEAD, hand.split(1));
            // 换下的头盔优先回到空出来的主手, 否则放回背包, 背包满了掉在脚下
            if (!helmet.isEmpty()) {
                if (hand.isEmpty()) {
                    handle.getInventory().setSelectedItem(helmet);
                } else {
                    handle.getInventory().placeItemBackInInventory(helmet);
                }
            }
            this.handleFeedback(context, (player == context.sender() ? MessageConstants.COMMAND_HAT_SUCCESS_SELF : MessageConstants.COMMAND_HAT_SUCCESS), Component.text(player.getName()));
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "hat";
    }
}
