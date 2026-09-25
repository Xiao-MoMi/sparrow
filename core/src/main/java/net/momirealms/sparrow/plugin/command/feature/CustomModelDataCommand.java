package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ModelDataParser;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class CustomModelDataCommand extends BukkitCommandFeature {
    public CustomModelDataCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.senderType(Player.class)
                .optional("value", ModelDataParser.modelDataParser())
                .handler(this::execute);
    }

    private void execute(CommandContext<Player> context) {
        Player player = context.sender();
        Number value = context.getOrDefault("value", null);
        this.plugin().scheduler().platform().run(() -> {
            ItemStack item = ((CraftPlayer) player).getHandle().getInventory().getSelectedItem();
            if (item.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_CUSTOM_MODEL_DATA_ITEMLESS);
                return;
            }
            CustomModelData data = item.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
            if (value == null) {
                Float current = data.getFloat(0);
                if (current == null) {
                    this.handleFeedback(context, MessageConstants.COMMAND_CUSTOM_MODEL_DATA_MISSING);
                } else {
                    this.handleFeedback(context, MessageConstants.COMMAND_CUSTOM_MODEL_DATA_QUERY, Component.text(current.toString()));
                }
                return;
            }
            List<Float> floats = new ArrayList<>(data.floats());
            if (floats.isEmpty()) {
                floats.add(value.floatValue());
            } else {
                floats.set(0, value.floatValue());
            }
            item.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.copyOf(floats), data.flags(), data.strings(), data.colors()));
            this.handleFeedback(context, MessageConstants.COMMAND_CUSTOM_MODEL_DATA_SUCCESS, Component.text(floats.getFirst().toString()));
        }, () -> {}, player);
    }

    @Override
    public String getFeatureID() {
        return "custom-model-data";
    }
}
