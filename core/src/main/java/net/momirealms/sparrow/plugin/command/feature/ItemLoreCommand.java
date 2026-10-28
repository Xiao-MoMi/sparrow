package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.TextPage;
import net.momirealms.sparrow.util.AdventureHelper;
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
import java.util.Optional;

public final class ItemLoreCommand extends BukkitCommandFeature {
    private static final String LORE_META_KEY = "sparrow_lore";

    public ItemLoreCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class)
                .flag(manager.flagBuilder("lore").withComponent(StringParser.greedyFlagYieldingStringParser()).build())
                .flag(manager.flagBuilder("operation").withComponent(EnumParser.enumParser(Operation.class)).build())
                .flag(manager.flagBuilder("line").withComponent(IntegerParser.integerParser(1)).build())
                .flag(manager.flagBuilder("internal").withComponent(IntegerParser.integerParser(1)).build())
                .flag(manager.flagBuilder("json").build())
                .flag(manager.flagBuilder("page").withComponent(IntegerParser.integerParser(1)).build())
                .handler(this::execute));
    }

    private void execute(CommandContext<Player> context) {
        Player player = context.sender();
        Runnable action = () -> {
            ItemStack item = this.plugin().playerManager().getOnlinePlayer(player).nmsPlayer.getMainHandItem();
            if (item.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_ITEMLESS);
                return;
            }
            List<net.minecraft.network.chat.Component> lines = new ArrayList<>(item.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
            Operation operation = context.flags().getValue("operation", null);
            if (operation == null) {
                this.show(player, item, lines, context.flags().getValue("page", 1));
                return;
            }
            if (!this.apply(context, lines, operation)) return;
            if (lines.isEmpty()) {
                item.remove(DataComponents.LORE);
            } else {
                item.set(DataComponents.LORE, new ItemLore(List.copyOf(lines)));
            }
            this.show(player, item, lines, context.flags().getValue("page", 1));
        };
        if (!context.flags().hasFlag("operation")) {
            action.run();
        } else {
            this.plugin().scheduler().platform().run(action, () -> {}, player);
        }
    }

    private boolean apply(CommandContext<Player> context, List<net.minecraft.network.chat.Component> lines, Operation operation) {
        Player player = context.sender();
        Integer line = context.flags().getValue("line", null);
        if (line == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_MISSING_FLAG, Component.text("--line"));
            return false;
        }
        Integer internal = context.flags().getValue("internal", null);
        if (internal != null && internal != this.internalId(player)) {
            this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_EXPIRED);
            return false;
        }
        int minimum = operation == Operation.UP ? 2 : 1;
        int maximum = lines.size() + (operation == Operation.INSERT ? 1 : (operation == Operation.DOWN ? -1 : 0));
        if (line < minimum || line > maximum) {
            this.handleFeedback(
                    context,
                    MessageConstants.COMMAND_ITEM_LORE_BOUND,
                    Component.text(line),
                    Component.text(maximum < minimum ? 0 : minimum),
                    Component.text(maximum < minimum ? 0 : maximum)
            );
            return false;
        }
        switch (operation) {
            case INSERT, EDIT -> {
                String input = context.flags().getValue("lore", null);
                if (input == null) {
                    this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_MISSING_FLAG, Component.text("--lore"));
                    return false;
                }
                if (operation == Operation.INSERT && lines.size() >= ItemLore.MAX_LINES) {
                    this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_LIMIT, Component.text(ItemLore.MAX_LINES));
                    return false;
                }
                net.minecraft.network.chat.Component text;
                try {
                    Component parsed = context.flags().hasFlag("json")
                            ? AdventureHelper.jsonToComponent(input)
                            : AdventureHelper.miniMessage("<!i><white>" + input, player);
                    text = CraftChatMessage.fromJSON(AdventureHelper.componentToJson(parsed));
                } catch (RuntimeException exception) {
                    this.handleFeedback(context, MessageConstants.COMMAND_ITEM_LORE_INVALID);
                    return false;
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
        return true;
    }

    private void show(Player player, ItemStack item, List<net.minecraft.network.chat.Component> lines, int requestedPage) {
        int pageSize = CommandPanel.list().getPageCapacity();
        int pages = Math.max(1, (lines.size() + pageSize - 1) / pageSize);
        int page = Math.min(requestedPage, pages);
        List<ItemLorePanel.Line> content = new ArrayList<>();
        for (int i = (page - 1) * pageSize, end = Math.min(page * pageSize, lines.size()); i < end; i++) {
            net.minecraft.network.chat.Component line = lines.get(i);
            String json = CraftChatMessage.toJSON(line);
            Component source = AdventureHelper.jsonToComponent(json);
            TextComponent.Builder display = Component.text();
            // 显示文本按服务端语言展开, 源码保留原组件结构.
            line.visit((style, text) -> {
                display.append(AdventureHelper.jsonToComponent(CraftChatMessage.toJSON(net.minecraft.network.chat.Component.literal(text).setStyle(style))));
                return Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY);
            content.add(new ItemLorePanel.Line(display.build(), json, AdventureHelper.miniMessage().serialize(source)));
        }
        int internal = this.internalId(player) + 1;
        player.setMetadata(LORE_META_KEY, new FixedMetadataValue(this.plugin().javaPlugin(), internal));
        ItemLorePanel.create(Component.text(item.getHoverName().getString()), new TextPage<>(page - 1, pageSize, lines.size(), content), internal).send(player);
    }

    private int internalId(Player player) {
        List<MetadataValue> valueValues = player.getMetadata(LORE_META_KEY);
        for (int valueIndex = 0, valueCount = valueValues.size(); valueIndex < valueCount; valueIndex++) {
            MetadataValue value = valueValues.get(valueIndex);
            if (value.getOwningPlugin() == this.plugin().javaPlugin()) {
                return value.asInt();
            }
        }
        return 0;
    }

    @Override
    public String getFeatureID() {
        return "item_lore";
    }

    public enum Operation {
        INSERT, REMOVE, EDIT, UP, DOWN
    }
}