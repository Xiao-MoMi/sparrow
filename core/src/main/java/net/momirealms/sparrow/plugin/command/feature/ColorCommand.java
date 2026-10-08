package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.minecraft.extras.parser.TextColorParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class ColorCommand extends BukkitCommandFeature {

    public ColorCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> command = builder.required("color", TextColorParser.textColorParser());
        manager.command(command.required("player", PlayerParser.playerParser())
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .permission(this.otherPermission(command))
                .handler(this::execute));
        manager.command(command.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        TextColor color = context.get("color");
        Component slotName = Component.text("hand");
        this.plugin().scheduler().platform().run(() -> {
                    ItemStack item = this.plugin().playerManager().getPlayer(player).nmsPlayer().getItemBySlot(EquipmentSlot.MAINHAND);
                    if (item.isEmpty()) {
                        this.handleFeedback(
                                context,
                                (player == context.sender() ? MessageConstants.COMMAND_COLOR_ITEMLESS_SELF : MessageConstants.COMMAND_COLOR_ITEMLESS),
                                Component.text(player.getName()),
                                slotName
                        );
                        return;
                    }
                    item.set(DataComponents.DYED_COLOR, new DyedItemColor(color.value()));
                    this.handleFeedback(
                            context,
                            (player == context.sender() ? MessageConstants.COMMAND_COLOR_SUCCESS_SELF : MessageConstants.COMMAND_COLOR_SUCCESS),
                            Component.text(player.getName()),
                            Component.text(color.asHexString(), color),
                            slotName
                    );
                }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "color";
    }
}
