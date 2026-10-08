package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class CustomNameCommand extends BukkitCommandFeature {

    public CustomNameCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", PlayerParser.playerParser())
                .optional("name", StringParser.greedyFlagYieldingStringParser())
                .flag(manager.flagBuilder("json").build())
                .flag(manager.flagBuilder("legacy-color").withAliases("l").build())
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        Player player = context.get("player");
        String input = context.getOrDefault("name", null);
        Runnable action = () -> {
            ItemStack item = this.plugin().playerManager().getPlayer(player).getItemInMainHand();
            if (item.isEmpty()) {
                this.handleFeedback(
                        context,
                        (player == context.sender() ? MessageConstants.COMMAND_CUSTOM_NAME_ITEMLESS_SELF
                                : MessageConstants.COMMAND_CUSTOM_NAME_ITEMLESS),
                        Component.text(player.getName())
                );
                return;
            }
            if (input != null) {
                Component name;
                net.minecraft.network.chat.Component minecraftName;
                try {
                    name = context.flags().hasFlag("json")
                            ? AdventureHelper.jsonToComponent(input)
                            : AdventureHelper.miniMessage("<!i>" + input, context.flags().hasFlag("legacy-color"), player);
                    minecraftName = CraftChatMessage.fromJSON(AdventureHelper.componentToJson(name));
                } catch (RuntimeException exception) {
                    this.handleFeedback(context, MessageConstants.COMMAND_CUSTOM_NAME_INVALID);
                    return;
                }
                item.set(DataComponents.CUSTOM_NAME, minecraftName);
                this.handleFeedback(
                        context,
                        (player == context.sender() ? MessageConstants.COMMAND_CUSTOM_NAME_SUCCESS_SELF
                                : MessageConstants.COMMAND_CUSTOM_NAME_SUCCESS),
                        name,
                        Component.text(player.getName())
                );
                return;
            }
            net.minecraft.network.chat.Component name = item.get(DataComponents.CUSTOM_NAME);
            if (name == null) {
                this.handleFeedback(
                        context,
                        (player == context.sender() ? MessageConstants.COMMAND_CUSTOM_NAME_UNNAMED_SELF
                                : MessageConstants.COMMAND_CUSTOM_NAME_UNNAMED),
                        Component.text(player.getName())
                );
                return;
            }
            String json = CraftChatMessage.toJSON(name);
            Component preview = AdventureHelper.jsonToComponent(json);
            String miniMessage = AdventureHelper.miniMessage().serialize(preview);
            String usage = this.commandConfig().getUsages().getFirst() + " " + player.getName();
            Component editHint = MessageConstants.COMMAND_CUSTOM_NAME_EDIT;
            Component jsonEditor = Component.text(json, NamedTextColor.GRAY)
                    .hoverEvent(editHint)
                    .clickEvent(ClickEvent.suggestCommand(usage + " " + json + " --json"));
            Component miniMessageEditor = Component.text(miniMessage, NamedTextColor.WHITE)
                    .hoverEvent(editHint)
                    .clickEvent(ClickEvent.suggestCommand(usage + " " + miniMessage));
            this.handleFeedback(
                    context,
                    (player == context.sender() ? MessageConstants.COMMAND_CUSTOM_NAME_QUERY_SELF : MessageConstants.COMMAND_CUSTOM_NAME_QUERY),
                    preview,
                    jsonEditor,
                    miniMessageEditor,
                    Component.text(player.getName())
            );
        };
        if (input == null) {
            action.run();
        } else {
            this.plugin().scheduler().platform().run(action, () -> {}, player);
        }
    }

    @Override
    public String getFeatureID() {
        return "custom-name";
    }
}