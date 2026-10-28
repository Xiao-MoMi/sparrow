package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.world.item.component.ItemLore;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.panel.PanelText;
import net.momirealms.sparrow.plugin.command.panel.PanelTone;
import net.momirealms.sparrow.plugin.command.panel.TextPage;

final class ItemLorePanel {
    private static final TextReplacementConfig LINE_BREAK = TextReplacementConfig.builder()
            .match("\\r\\n|\\r|\\n")
            .replacement(Component.text(" ↵ ", PanelTone.MUTED.color()))
            .build();

    private ItemLorePanel() {
    }

    static CommandPanel create(Component itemName, TextPage<Line> page, int version) {
        Component edit = tr("edit");
        Component remove = tr("remove");
        Component up = Component.text("↑");
        Component down = Component.text("↓");
        int current = page.index() + 1;
        String insert = "--operation insert --line " + (page.total() + 1) + " --internal " + version + " --page " + current + " --lore ";
        CommandPanel.Builder panel = CommandPanel.list()
                .title(tr("title", itemName))
                .status(PanelText.of(tr("total", Component.text(page.total()))).tone(PanelTone.MUTED))
                .titleActions(PanelButton.suggest(tr("add"), "item_lore", insert)
                        .tone(PanelTone.SUCCESS)
                        .disabledIf(page.total() >= ItemLore.MAX_LINES, MessageConstants.COMMAND_ITEM_LORE_LIMIT.arguments(Component.text(ItemLore.MAX_LINES))))
                .actionColumn("edit", edit)
                .actionColumn("remove", remove)
                .actionColumn("up", up)
                .actionColumn("down", down);
        for (int i = 0, size = page.content().size(); i < size; i++) {
            Line source = page.content().get(i);
            int number = page.index() * page.size() + i + 1;
            String target = " --line " + number + " --internal " + version + " --page " + current;
            Component preview = source.preview().applyFallbackStyle(Style.style(NamedTextColor.DARK_PURPLE, TextDecoration.ITALIC));
            Component display = PlainTextComponentSerializer.plainText().serialize(preview).isEmpty()
                    ? tr("blank").color(PanelTone.MUTED.color())
                    : preview.replaceText(LINE_BREAK);
            panel.indexedEntry(
                    PanelText.of(number + ".").copy(source.json()).hover(tr("json-source", Component.text(source.json()))),
                    PanelText.of(display).copy(source.miniMessage()).hover(tr("mini-source", preview, Component.text(source.miniMessage()))),
                    PanelButton.suggest(edit, "item_lore", "--operation edit" + target + " --lore " + source.miniMessage()).column("edit"),
                    PanelButton.run(remove, "item_lore", "--operation remove" + target).tone(PanelTone.DANGER).column("remove"),
                    PanelButton.run(up, "item_lore", "--operation up" + target).disabledIf(number == 1, tr("first-line")).column("up"),
                    PanelButton.run(down, "item_lore", "--operation down" + target).disabledIf(number == page.total(), tr("last-line")).column("down")
            );
        }
        if (page.content().isEmpty()) {
            panel.entry(PanelText.of(tr("empty")).tone(PanelTone.MUTED));
        }
        return panel.pagination(page, "item_lore", number -> "--page " + number).build();
    }

    private static Component tr(String key, Component... arguments) {
        return Component.translatable("command.item-lore.panel." + key, arguments);
    }

    record Line(Component preview, String json, String miniMessage) {
    }
}