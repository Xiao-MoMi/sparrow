package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.OptionalTextColorParser;
import net.momirealms.sparrow.proxy.bukkit.inventory.CraftItemStackProxy;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.EnumParser;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

public final class ColorCommand extends BukkitCommandFeature {
    public ColorCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.optional("color", OptionalTextColorParser.optionalTextColorParser())
                .flag(manager.flagBuilder("player").withComponent(PlayerParser.playerParser()))
                .flag(manager.flagBuilder("slot").withComponent(EnumParser.enumParser(EquipmentSlot.class)))
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.flags().getValue("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        TextColor color = context.<Optional<TextColor>>getOrDefault("color", Optional.empty()).orElse(null);
        EquipmentSlot slot = context.flags().getValue("slot", EquipmentSlot.HAND);
        Component slotName = Component.text(slot.name().toLowerCase(Locale.ROOT));
        this.plugin().scheduler().platform().run(() -> {
            ItemStack item = CraftItemStack.asNMSCopy(player.getInventory().getItem(slot));
            if (item.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_COLOR_ITEMLESS, Component.text(player.getName()), slotName);
                return;
            }
            if (color != null) {
                item.set(DataComponents.DYED_COLOR, new DyedItemColor(color.value()));
                player.getInventory().setItem(slot, CraftItemStackProxy.INSTANCE.asBukkitMirror(item));
                this.handleFeedback(context, MessageConstants.COMMAND_COLOR_SUCCESS, Component.text(player.getName()), Component.text(color.asHexString(), color), slotName);
                return;
            }
            DyedItemColor dyedColor = item.get(DataComponents.DYED_COLOR);
            if (dyedColor == null) {
                this.handleFeedback(context, MessageConstants.COMMAND_COLOR_MISSING, Component.text(player.getName()), slotName);
                return;
            }
            TextColor current = TextColor.color(dyedColor.rgb());
            Component hex = Component.text(current.asHexString(), current).clickEvent(ClickEvent.copyToClipboard(current.asHexString()));
            this.handleFeedback(context, MessageConstants.COMMAND_COLOR_QUERY, Component.text(player.getName()), hex,
                    Component.text(current.red()), Component.text(current.green()), Component.text(current.blue()), Component.text(current.value()), slotName);
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "color";
    }
}
