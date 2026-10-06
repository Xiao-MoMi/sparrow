package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.AdventureHelper;
import net.momirealms.sparrow.util.Components;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ItemLoreCommand extends BukkitCommandFeature {
    private static final String LORE_META_KEY = "sparrow_lore";

    public ItemLoreCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.senderType(Player.class)
                .flag(manager.flagBuilder("legacy-color").withAliases("l").build())
                .flag(manager.flagBuilder("lore").withComponent(StringParser.greedyFlagYieldingStringParser()).build())
                .flag(manager.flagBuilder("operation").withComponent(EnumParser.enumParser(Operation.class)).build())
                .flag(manager.flagBuilder("line").withComponent(IntegerParser.integerParser(1)).build())
                .flag(manager.flagBuilder("internal").withComponent(IntegerParser.integerParser(1)).build())
                .flag(manager.flagBuilder("json").build())
                .handler(this::execute);
    }

    private void execute(CommandContext<Player> context) {
        Player player = context.sender();
        this.plugin().scheduler().platform().run(() -> {
            ItemStack item = this.plugin().playerManager().getPlayer(player).getItemInMainHand();
            if (item.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_ITEMLESS);
                return;
            }
            List<net.minecraft.network.chat.Component> lines = new ArrayList<>(item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
            Operation operation = context.flags().getValue("operation", null);
            if (operation == null) {
                this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_QUERY, this.getQueryResult(player, lines));
                return;
            }
            Integer line = context.flags().getValue("line", null);
            if (line == null) {
                this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_MISSING_FLAG, Component.text("--line"));
                return;
            }
            if (operation == Operation.REMOVE || operation == Operation.UP || operation == Operation.DOWN) {
                Integer internal = context.flags().getValue("internal", null);
                if (internal != null && internal != this.internalId(player)) {
                    this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_EXPIRED);
                    return;
                }
            }
            int minimum = operation == Operation.UP ? 2 : 1;
            int maximum = lines.size() + (operation == Operation.INSERT ? 1 : operation == Operation.DOWN ? -1 : 0);
            if (line < minimum || line > maximum) {
                this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_BOUND, Component.text(line),
                        Component.text(maximum < minimum ? 0 : minimum), Component.text(maximum < minimum ? 0 : maximum));
                return;
            }
            switch (operation) {
                case INSERT, EDIT -> {
                    String input = context.flags().getValue("lore", null);
                    if (input == null) {
                        this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_MISSING_FLAG, Component.text("--lore"));
                        return;
                    }
                    if (operation == Operation.INSERT && lines.size() >= ItemLore.MAX_LINES) {
                        this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_LIMIT, Component.text(ItemLore.MAX_LINES));
                        return;
                    }
                    net.minecraft.network.chat.Component text;
                    try {
                        Component parsed = context.flags().hasFlag("json") ? AdventureHelper.jsonToComponent(input)
                                : Components.miniMessage("<!i><white>" + input, context.flags().hasFlag("legacy-color"));
                        text = CraftChatMessage.fromJSON(AdventureHelper.componentToJson(parsed));
                    } catch (RuntimeException exception) {
                        this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_INVALID);
                        return;
                    }
                    if (operation == Operation.INSERT) {
                        lines.add(line - 1, text);
                    } else {
                        lines.set(line - 1, text);
                    }
                }
                case REMOVE -> lines.remove(line - 1);
                case UP -> Collections.swap(lines, line - 1, line - 2);
                case DOWN -> Collections.swap(lines, line - 1, line);
            }
            if (lines.isEmpty()) {
                item.remove(DataComponents.LORE);
            } else {
                item.set(DataComponents.LORE, new ItemLore(List.copyOf(lines)));
            }
            this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_SUCCESS, this.getQueryResult(player, lines));
        }, () -> {}, player);
    }

    private Component getQueryResult(Player player, List<net.minecraft.network.chat.Component> lines) {
        int internal = this.internalId(player) + 1;
        player.setMetadata(LORE_META_KEY, new FixedMetadataValue(this.plugin().javaPlugin(), internal));
        String usage = this.commandConfig().getUsages().getFirst();
        TextComponent.Builder result = Component.text();
        Component editHint = MessageConstants.COMMAND_ITEM_LORE_EDIT.asComponent();
        for (int i = 0, size = lines.size(); i < size; i++) {
            String json = CraftChatMessage.toJSON(lines.get(i));
            Component preview = AdventureHelper.jsonToComponent(json);
            String miniMessage = AdventureHelper.miniMessage().serialize(preview);
            Component source = Component.text("JSON: " + json, NamedTextColor.GRAY).append(Component.newline())
                    .append(Component.text("MiniMessage: " + miniMessage, NamedTextColor.WHITE));
            int line = i + 1;
            String action = usage + " --operation ";
            String target = " --line " + line + " --internal " + internal;
            result.append(Component.text("[" + line + "] ", NamedTextColor.YELLOW).hoverEvent(editHint)
                    .clickEvent(ClickEvent.suggestCommand(action + "edit --line " + line + " --json --lore " + json)));
            result.append(preview.applyFallbackStyle(Style.style(NamedTextColor.DARK_PURPLE, TextDecoration.ITALIC)).hoverEvent(source)
                    .clickEvent(ClickEvent.suggestCommand(action + "edit --line " + line + " --lore " + miniMessage)));
            result.append(Component.space());
            result.append(Component.text("[X]", TextColor.color(0xDC143C)).hoverEvent(MessageConstants.COMMAND_ITEM_LORE_DELETE.asComponent())
                    .clickEvent(ClickEvent.runCommand(action + "remove" + target)));
            if (i > 0) {
                result.append(Component.space());
                result.append(Component.text("[↑]", TextColor.color(0x7B68EE)).hoverEvent(MessageConstants.COMMAND_ITEM_LORE_UP.asComponent())
                        .clickEvent(ClickEvent.runCommand(action + "up" + target)));
            }
            if (i < size - 1) {
                result.append(Component.space());
                result.append(Component.text("[↓]", TextColor.color(0xDA70D6)).hoverEvent(MessageConstants.COMMAND_ITEM_LORE_DOWN.asComponent())
                        .clickEvent(ClickEvent.runCommand(action + "down" + target)));
            }
            result.append(Component.newline());
        }
        result.append(Component.text("[" + (lines.size() + 1) + "] ", NamedTextColor.YELLOW));
        result.append(Component.text("[+]", NamedTextColor.GREEN).hoverEvent(MessageConstants.COMMAND_ITEM_LORE_INSERT.asComponent())
                .clickEvent(ClickEvent.suggestCommand(usage + " --operation insert --line " + (lines.size() + 1) + " --lore ")));
        return result.asComponent();
    }

    private int internalId(Player player) {
        for (MetadataValue value : player.getMetadata(LORE_META_KEY)) {
            if (value.getOwningPlugin() == this.plugin().javaPlugin()) return value.asInt();
        }
        return 0;
    }

    @Override
    public String getFeatureID() {
        return "item-lore";
    }

    public enum Operation {
        INSERT, REMOVE, EDIT, UP, DOWN
    }
}
