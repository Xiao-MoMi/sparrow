package net.momirealms.sparrow.plugin.command.panel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PanelLayout {
    private static final int WIDTH = 300;
    private static final int INDENT = 8;
    private static final int LABEL_WIDTH = 40;
    private static final int ACTION_GAP = 16;

    private final CommandPanel panel;
    private final PanelContext context;
    private final List<Column> columns = new ArrayList<>();

    PanelLayout(CommandPanel panel, PanelContext context) {
        this.panel = panel;
        this.context = context;
        if (!context.interactive) return;
        int right = WIDTH;
        for (int i = panel.columns.size() - 1; i >= 0; i--) {
            CommandPanel.ActionColumn declaration = panel.columns.get(i);
            double maximum = 0;
            double[] widths = new double[declaration.captions().size()];
            for (int captionIndex = 0; captionIndex < widths.length; captionIndex++) {
                Component caption = declaration.captions().get(captionIndex);
                Component text = Component.text("[").append(context.caption(caption, PanelTone.ACTION)).append(Component.text("]"));
                widths[captionIndex] = PanelTypography.width(text);
                maximum = Math.max(maximum, widths[captionIndex]);
            }
            int width = (int) Math.ceil(maximum);
            for (int captionIndex = 0; captionIndex < widths.length;) {
                if (PanelTypography.canPad((int) Math.floor(width - widths[captionIndex]))) {
                    captionIndex++;
                } else {
                    width++;
                    captionIndex = 0;
                }
            }
            this.columns.addFirst(new Column(declaration.key(), right - width, right, maximum));
            right -= width + 4;
        }
        if (right < INDENT + LABEL_WIDTH + ACTION_GAP) throw new IllegalStateException("Panel action columns leave no room for content");
    }

    Component render() {
        List<Component> lines = new ArrayList<>();
        lines.add(this.header());
        if (this.panel.summary != null) {
            lines.add(this.content(Component.empty(), this.panel.summary, List.of()));
        }
        if (this.panel.rows.isEmpty()) {
            lines.add(this.context.text(Component.translatable("command.panel.empty"), PanelTone.MUTED));
        }
        for (int i = 0, size = this.panel.rows.size(); i < size; i++) {
            switch (this.panel.rows.get(i)) {
                case CommandPanel.Section section -> {
                    Component title = Component.text("─ ", PanelTone.MUTED.color()).append(this.context.text(section.title(), PanelTone.TITLE));
                    lines.add(this.context.interactive ? PanelTypography.fit(title, WIDTH, false) : title);
                }
                case CommandPanel.Field field -> {
                    Component label = this.context.text(field.label(), PanelTone.LABEL);
                    Component prefix = this.context.interactive
                            ? PanelTypography.padding(INDENT).append(PanelTypography.cell(label, LABEL_WIDTH))
                            : label.append(Component.text(": ", PanelTone.LABEL.color()));
                    lines.add(this.content(prefix, field.value(), field.actions()));
                }
                case CommandPanel.Paragraph paragraph -> {
                    Component label = this.context.text(paragraph.label(), PanelTone.LABEL);
                    Component value = this.context.text(paragraph.value(), PanelTone.BODY);
                    if (this.context.interactive) {
                        List<Component> wrapped = PanelTypography.paragraph(value, WIDTH - INDENT - LABEL_WIDTH);
                        for (int line = 0; line < wrapped.size(); line++) {
                            Component prefix = PanelTypography.padding(INDENT).append(PanelTypography.cell(line == 0 ? label : Component.empty(), LABEL_WIDTH));
                            lines.add(prefix.append(wrapped.get(line)));
                        }
                    } else {
                        lines.add(label.append(Component.text(": ")).append(value));
                    }
                }
                case CommandPanel.Entry entry -> lines.add(this.content(this.context.interactive ? PanelTypography.padding(INDENT) : Component.empty(), entry.value(), entry.actions()));
                case CommandPanel.RecordEntry entry -> lines.add(this.record(entry));
                case CommandPanel.NamedEntry entry -> lines.add(this.named(entry));
                case CommandPanel.DatedEntry entry -> lines.add(this.dated(entry));
                case CommandPanel.IndexedEntry entry -> {
                    Component index = this.context.text(entry.index(), PanelTone.TIME);
                    Component prefix = this.context.interactive
                            ? PanelTypography.padding(INDENT).append(PanelTypography.cell(index, 32))
                            : index.append(Component.space());
                    lines.add(this.content(prefix, entry.value(), entry.actions()));
                }
            }
        }
        this.footer(lines);
        if (this.panel.list && this.context.interactive && lines.size() > 10) throw new IllegalStateException("List panel exceeds ten content lines");
        if (this.context.interactive) {
            lines.addAll(0, List.of(Component.empty(), Component.empty()));
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private Component header() {
        Component marker = Component.text("◆ ", PanelTone.TITLE.color());
        Component title = this.context.text(this.panel.title, PanelTone.TITLE).decorate(TextDecoration.BOLD);
        List<Component> buttons = new ArrayList<>();
        if (this.panel.list && this.panel.pagination != null) {
            buttons.add(this.refresh(this.panel.pagination));
        }
        this.panel.titleActions.forEach(button -> buttons.add(button.render(this.context)));
        Component actions = Component.join(JoinConfiguration.separator(PanelTypography.padding(4)), buttons);
        Component status = this.panel.status == null ? Component.empty() : this.context.text(this.panel.status, PanelTone.MUTED);
        if (!this.context.interactive) {
            return marker.append(title).append(Component.space()).append(actions).append(Component.space()).append(status);
        }
        double actionWidth = buttons.isEmpty() ? 0 : PanelTypography.width(actions) + 4;
        double statusWidth = this.panel.status == null ? 0 : PanelTypography.width(status) + ACTION_GAP;
        double budget = WIDTH - PanelTypography.width(marker) - actionWidth - statusWidth;
        if (budget < 9) throw new IllegalStateException("Panel header actions and status exceed its width");
        Component left = marker.append(PanelTypography.fit(title, budget, false));
        if (!buttons.isEmpty()) {
            left = left.append(PanelTypography.padding(4)).append(actions);
        }
        return this.panel.status == null ? left : this.right(left, status);
    }

    private Component content(Component prefix, PanelText text, List<PanelButton> actions) {
        Component value = this.context.text(text, PanelTone.BODY);
        if (!this.context.interactive) {
            Component result = prefix.append(value);
            for (PanelButton button : actions) {
                result = result.append(Component.space()).append(button.render(this.context));
            }
            return result;
        }

        Map<String, Component> buttons = this.actions(actions);
        double edge = this.contentEdge(buttons);
        double prefixWidth = PanelTypography.width(prefix);
        if (edge - prefixWidth < 9) throw new IllegalStateException("Panel row leaves no room for its value");
        Component result = Component.empty().append(prefix).append(PanelTypography.fit(value, edge - prefixWidth, text.wholeWords()));
        return this.placeActions(result, buttons);
    }

    private Component record(CommandPanel.RecordEntry entry) {
        Component reference = this.context.text(entry.reference(), PanelTone.ACTION);
        Component status = this.context.text(entry.status(), PanelTone.MUTED);
        Component subject = this.context.text(entry.subject(), PanelTone.INFO);
        Component time = this.context.text(entry.time(), PanelTone.TIME);
        if (!this.context.interactive) {
            Component row = Component.join(JoinConfiguration.separator(Component.space()), reference, status, subject, time);
            for (PanelButton button : entry.actions()) {
                row = row.append(Component.space()).append(button.render(this.context));
            }
            return row;
        }
        Map<String, Component> buttons = this.actions(entry.actions());
        double edge = this.contentEdge(buttons);
        if (!this.columns.isEmpty()) {
            edge = Math.min(edge, this.columns.getFirst().left - ACTION_GAP);
        }
        int subjectWidth = (int) edge - INDENT - 44 - 52 - 60;
        if (subjectWidth < 16) throw new IllegalStateException("Record columns leave no room for the subject");
        Component row = Component.empty()
                .append(PanelTypography.padding(INDENT))
                .append(PanelTypography.cell(reference, 44))
                .append(PanelTypography.cell(status, 52))
                .append(PanelTypography.cell(subject, subjectWidth))
                .append(PanelTypography.cell(time, 60));
        return this.placeActions(row, buttons);
    }

    private Component named(CommandPanel.NamedEntry entry) {
        Component name = this.context.text(entry.name(), PanelTone.BODY);
        Component value = this.context.text(entry.value(), entry.status() ? PanelTone.MUTED : PanelTone.INFO);
        if (!this.context.interactive) {
            Component row = Component.empty().append(name).append(Component.text(" · ")).append(value);
            for (PanelButton button : entry.actions()) {
                row = row.append(Component.space()).append(button.render(this.context));
            }
            return row;
        }
        Map<String, Component> buttons = this.actions(entry.actions());
        double edge = this.contentEdge(buttons);
        if (!this.columns.isEmpty()) {
            edge = Math.min(edge, this.columns.getFirst().left - ACTION_GAP);
        }
        int nameWidth = entry.status() ? 154 : 100;
        double valueWidth = edge - INDENT - nameWidth;
        if (valueWidth < 9) throw new IllegalStateException("Named columns leave no room for their value");
        Component row = Component.empty()
                .append(PanelTypography.padding(INDENT))
                .append(PanelTypography.nameCell(name, nameWidth))
                .append(PanelTypography.fit(value, valueWidth, false));
        return this.placeActions(row, buttons);
    }

    private Component dated(CommandPanel.DatedEntry entry) {
        Component value = this.context.text(entry.value(), PanelTone.INFO);
        Component badge = this.context.text(entry.badge(), PanelTone.MUTED);
        Component time = this.context.text(entry.time(), PanelTone.TIME);
        if (!this.context.interactive) {
            Component result = Component.join(JoinConfiguration.separator(Component.space()), value, badge, time);
            for (PanelButton action : entry.actions()) {
                result = result.append(Component.space()).append(action.render(this.context));
            }
            return result;
        }
        Map<String, Component> buttons = this.actions(entry.actions());
        double edge = this.contentEdge(buttons);
        if (!this.columns.isEmpty()) edge = Math.min(edge, this.columns.getFirst().left - ACTION_GAP);
        int badgeWidth = entry.leading() ? 17 : 40;
        int valueWidth = (int) edge - INDENT - badgeWidth - 60 - ACTION_GAP;
        if (valueWidth < 16) throw new IllegalStateException("History columns leave no room for their value");
        Component valueCell = PanelTypography.nameCell(value, valueWidth);
        Component badgeCell = PanelTypography.cell(badge, badgeWidth);
        Component result = Component.empty()
                .append(PanelTypography.padding(INDENT))
                .append(entry.leading() ? badgeCell : valueCell)
                .append(entry.leading() ? valueCell : badgeCell)
                .append(PanelTypography.padding(ACTION_GAP))
                .append(PanelTypography.cell(time, 60));
        return this.placeActions(result, buttons);
    }

    private Map<String, Component> actions(List<PanelButton> actions) {
        Map<String, Component> buttons = new HashMap<>();
        for (PanelButton button : actions) {
            if (button.column() == null || buttons.put(button.column(), button.render(this.context)) != null) {
                throw new IllegalStateException("Every row action needs a distinct declared column");
            }
        }
        return buttons;
    }

    private double contentEdge(Map<String, Component> buttons) {
        double edge = WIDTH;
        int matched = 0;
        for (Column column : this.columns) {
            Component button = buttons.get(column.key);
            if (button == null) continue;
            if (PanelTypography.width(button) > column.maximum) throw new IllegalStateException("Button exceeds its declared column: " + column.key);
            if (matched++ == 0) edge = column.left - ACTION_GAP;
        }
        if (matched != buttons.size()) throw new IllegalStateException("Row action uses an undeclared column");
        return edge;
    }

    private Component placeActions(Component result, Map<String, Component> buttons) {
        for (Column column : this.columns) {
            Component button = buttons.get(column.key);
            if (button == null) continue;
            double padding = column.right - PanelTypography.width(button) - PanelTypography.width(result);
            result = result.append(PanelTypography.padding(padding)).append(button);
        }
        return result;
    }

    private void footer(List<Component> lines) {
        List<Component> actions = this.panel.footer.stream().map(button -> button.render(this.context)).toList();
        if (this.panel.list && this.panel.pagination != null) {
            lines.add(this.pagination(this.panel.pagination, actions));
            return;
        }
        List<Component> current = new ArrayList<>();
        double width = 0;
        for (Component action : actions) {
            double next = PanelTypography.width(action);
            if (this.context.interactive && next > WIDTH) throw new IllegalStateException("A panel footer button exceeds its width");
            if (this.context.interactive && width + (current.isEmpty() ? 0 : 4) + next > WIDTH) {
                lines.add(Component.join(JoinConfiguration.separator(Component.space()), current));
                current.clear();
                width = 0;
            }
            width += (current.isEmpty() ? 0 : 4) + next;
            current.add(action);
        }
        if (!current.isEmpty()) {
            Component footer = Component.join(JoinConfiguration.separator(Component.space()), current);
            if (this.panel.hint != null && this.panel.pagination == null) {
                Component hint = this.context.text(this.panel.hint, PanelTone.MUTED);
                double budget = WIDTH - PanelTypography.width(footer) - ACTION_GAP;
                if (!this.context.interactive) footer = footer.append(Component.space()).append(hint);
                else if (budget >= 9) footer = this.right(footer, PanelTypography.fit(hint, budget, false));
            }
            lines.add(footer);
        }
        if (this.panel.pagination != null) {
            lines.add(this.pagination(this.panel.pagination, List.of()));
        } else if (this.panel.list && current.isEmpty()) {
            lines.add(this.context.text(Component.translatable("command.panel.hover-hint"), PanelTone.MUTED));
        }
    }

    private Component pagination(CommandPanel.Pagination pagination, List<Component> actions) {
        Component previous = PanelButton.run(Component.translatable("command.panel.previous"), pagination.commandId(), pagination.previous())
                .disabledIf(pagination.page() == 1, Component.translatable("command.panel.first-page"))
                .render(this.context);
        Component next = PanelButton.run(Component.translatable("command.panel.next"), pagination.commandId(), pagination.next())
                .disabledIf(pagination.page() == pagination.pages(), Component.translatable("command.panel.last-page"))
                .render(this.context);
        Component navigation = Component.empty()
                .append(previous)
                .append(Component.text(" " + pagination.page() + "/" + pagination.pages() + " ", PanelTone.TITLE.color()))
                .append(next);
        for (Component action : actions) {
            navigation = navigation.append(Component.space()).append(action);
        }
        if (this.panel.list) return navigation;
        Component refresh = this.refresh(pagination);
        return this.context.interactive ? this.right(navigation, refresh) : navigation.append(Component.space()).append(refresh);
    }

    private Component refresh(CommandPanel.Pagination pagination) {
        return PanelButton.run(Component.translatable("command.panel.refresh"), pagination.commandId(), pagination.refresh()).render(this.context);
    }

    private Component right(Component left, Component right) {
        double gap = WIDTH - PanelTypography.width(left) - PanelTypography.width(right);
        if (gap < ACTION_GAP) throw new IllegalStateException("Panel fixed content exceeds its width");
        return Component.empty().append(left).append(PanelTypography.padding(gap)).append(right);
    }

    private record Column(String key, int left, int right, double maximum) {
    }
}