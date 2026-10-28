package net.momirealms.sparrow.plugin.command.panel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class PanelTypography {
    private static final Style SPACING = Style.style()
            .decoration(TextDecoration.BOLD, false)
            .decoration(TextDecoration.ITALIC, false)
            .decoration(TextDecoration.UNDERLINED, false)
            .decoration(TextDecoration.STRIKETHROUGH, false)
            .decoration(TextDecoration.OBFUSCATED, false)
            .build();

    private PanelTypography() {
    }

    static double width(Component component) {
        double width = 0;
        List<Run> runs = runs(component);
        for (int i = 0, size = runs.size(); i < size; i++) {
            Run run = runs.get(i);
            for (int offset = 0; offset < run.text.length();) {
                int codePoint = run.text.codePointAt(offset);
                width += advance(codePoint, run.style);
                offset += Character.charCount(codePoint);
            }
        }
        return width;
    }

    private static double advance(int codePoint, Style style) {
        // 默认 ASCII 和面板符号使用固定字宽, 其余字符按全角估算.
        int width = switch (codePoint) {
            case '!', '\'', ',', '.', ':', ';', 'i', '|', '·' -> 2;
            case '`', 'l', '↵' -> 3;
            case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k', '●' -> 5;
            case '◆', '○', '↑', '↓' -> 6;
            case '@', '~' -> 7;
            case '…' -> 8;
            default -> codePoint < 128 ? 6 : 9;
        };
        if (style.decoration(TextDecoration.BOLD) != TextDecoration.State.TRUE) return width;
        double bold = switch (codePoint) {
            case '◆', '●', '○', '·', '─', '…', '↑', '↓' -> 1;
            default -> codePoint < 128 ? 1 : 0.5;
        };
        return width + bold;
    }

    static Component singleLine(Component component) {
        TextComponent.Builder result = Component.text();
        for (Run run : runs(component)) {
            result.append(Component.text(run.text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' '), run.style));
        }
        return result.build();
    }

    static Component withoutEvents(Component component) {
        Component result = component.clickEvent(null).hoverEvent(null).insertion(null);
        if (result instanceof TranslatableComponent translated) {
            result = translated.arguments(translated.arguments().stream()
                    .map(argument -> argument.value() instanceof Component value ? TranslationArgument.component(withoutEvents(value)) : argument)
                    .toList());
        }
        return result.children(result.children().stream().map(PanelTypography::withoutEvents).toList());
    }

    static Component console(Component component) {
        Component result = withoutEvents(component);
        List<Component> tips = new ArrayList<>();
        for (Run run : runs(component)) {
            HoverEvent<?> hover = run.style.hoverEvent();
            if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT) {
                Component tip = withoutEvents((Component) hover.value());
                if (!tips.contains(tip)) tips.add(tip);
            }
        }
        for (Component tip : tips) {
            result = result.append(Component.text(" (", PanelTone.MUTED.color())).append(tip).append(Component.text(")", PanelTone.MUTED.color()));
        }
        return result;
    }

    static Component fit(Component original, double budget, boolean wholeWords) {
        Component line = singleLine(original);
        boolean multiline = runs(original).stream().anyMatch(run -> run.text.indexOf('\n') >= 0 || run.text.indexOf('\r') >= 0);
        if (width(line) <= budget && !multiline) return line;
        if (width(line) <= budget) return tooltip(line, original);

        List<Run> runs = runs(line);
        Style style = runs.isEmpty() ? Style.empty() : runs.getFirst().style;
        double available = budget - advance('…', style);
        TextComponent.Builder result = Component.text();
        double used = 0;
        int lastSpace = 0;
        int length = 0;
        List<Run> accepted = new ArrayList<>();
        for (int i = 0, size = runs.size(); i < size; i++) {
            Run run = runs.get(i);
            int offset = 0;
            while (offset < run.text.length()) {
                int codePoint = run.text.codePointAt(offset);
                double next = advance(codePoint, run.style);
                if (used + next > available) break;
                if (codePoint == ' ') lastSpace = length + offset;
                used += next;
                offset += Character.charCount(codePoint);
            }
            if (offset != 0) accepted.add(new Run(run.text.substring(0, offset), run.style));
            length += offset;
            if (offset != run.text.length()) break;
        }
        int remaining = wholeWords ? lastSpace : length;
        for (int i = 0, size = accepted.size(); i < size && remaining > 0; i++) {
            Run run = accepted.get(i);
            int count = Math.min(remaining, run.text.length());
            result.append(Component.text(run.text.substring(0, count), run.style));
            remaining -= count;
        }
        if (budget >= advance('…', style)) {
            result.append(Component.text("…", style));
        }
        return tooltip(result.build(), original);
    }

    static List<Component> paragraph(Component original, int width) {
        Component line = singleLine(original);
        if (width(line) <= width) return List.of(fit(original, width, false));
        List<Run> runs = runs(line);
        int length = 0;
        int lastSpace = -1;
        double used = 0;
        outer:
        for (Run run : runs) {
            for (int offset = 0; offset < run.text.length();) {
                int codePoint = run.text.codePointAt(offset);
                double next = advance(codePoint, run.style);
                if (used + next > width) break outer;
                if (codePoint == ' ') lastSpace = length;
                used += next;
                int count = Character.charCount(codePoint);
                length += count;
                offset += count;
            }
        }
        // 有空格时在词之间换行, 中文等连续文本按完整码点换行.
        int end = lastSpace > 0 ? lastSpace : length;
        int next = lastSpace > 0 ? lastSpace + 1 : length;
        return List.of(tooltip(slice(runs, 0, end), original), tooltip(fit(slice(runs, next, Integer.MAX_VALUE), width, false), original));
    }

    private static Component slice(List<Run> runs, int start, int end) {
        TextComponent.Builder result = Component.text();
        int offset = 0;
        for (Run run : runs) {
            int from = Math.max(0, start - offset);
            int to = Math.min(run.text.length(), end - offset);
            if (from < to) result.append(Component.text(run.text.substring(from, to), run.style));
            offset += run.text.length();
            if (offset >= end) break;
        }
        return result.build();
    }

    private static Component tooltip(Component clipped, Component original) {
        Component full = withoutEvents(original);
        List<Component> tips = new ArrayList<>();
        for (Run run : runs(original)) {
            HoverEvent<?> hover = run.style.hoverEvent();
            if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT) {
                Component tip = (Component) hover.value();
                if (!tips.contains(tip)) tips.add(tip);
            }
        }
        for (Component tip : tips) {
            full = full.append(Component.newline()).append(tip);
        }
        TextComponent.Builder result = Component.text();
        for (Run run : runs(clipped)) {
            result.append(Component.text(run.text, run.style.hoverEvent(null)));
        }
        return result.hoverEvent(full).build();
    }

    static boolean canPad(int pixels) {
        return pixels >= 0 && pixels >= pixels % 4 * 5;
    }

    static Component padding(double pixels) {
        int whole = (int) Math.floor(pixels);
        if (!canPad(whole)) throw new IllegalArgumentException("Unrepresentable panel spacing: " + pixels);
        int bold = whole % 4;
        int normal = (whole - bold * 5) / 4;
        return Component.empty()
                .append(Component.text(" ".repeat(normal), SPACING))
                .append(Component.text(" ".repeat(bold), SPACING.decoration(TextDecoration.BOLD, true)));
    }

    static Component cell(Component component, int width) {
        double budget = width;
        Component fitted = fit(component, budget, false);
        while (!canPad((int) Math.floor(width - width(fitted)))) {
            fitted = fit(component, --budget, false);
        }
        return Component.empty().append(fitted).append(padding(width - width(fitted)));
    }

    static Component nameCell(Component original, int width) {
        int budget = width - 4;
        Component fitted = fitName(original, budget);
        while (!canPad((int) Math.floor(width - width(fitted)))) {
            fitted = fitName(original, --budget);
        }
        return Component.empty().append(fitted).append(padding(width - width(fitted)));
    }

    private static Component fitName(Component original, int budget) {
        Component line = singleLine(original);
        if (width(line) <= budget) return fit(original, budget, false);
        List<Run> runs = runs(line);
        StringBuilder text = new StringBuilder();
        for (Run run : runs) {
            text.append(run.text);
        }
        int count = text.codePointCount(0, text.length());
        int tailStart = text.offsetByCodePoints(text.length(), -Math.min(3, count));
        Component tail = slice(runs, tailStart, Integer.MAX_VALUE);
        Style style = runs.getFirst().style;
        double available = budget - width(tail) - advance('…', style);
        if (available < 0) return fit(original, budget, false);
        int prefixEnd = 0;
        double used = 0;
        outer:
        for (Run run : runs) {
            for (int offset = 0; offset < run.text.length();) {
                int codePoint = run.text.codePointAt(offset);
                double next = advance(codePoint, run.style);
                if (prefixEnd >= tailStart || used + next > available) break outer;
                int length = Character.charCount(codePoint);
                used += next;
                offset += length;
                prefixEnd += length;
            }
        }
        Component result = Component.empty()
                .append(slice(runs, 0, prefixEnd))
                .append(Component.text("…", style))
                .append(tail);
        return tooltip(result, original);
    }

    private static List<Run> runs(Component component) {
        List<Run> runs = new ArrayList<>();
        Deque<Style> styles = new ArrayDeque<>();
        styles.push(Style.empty());
        ComponentFlattener.basic().flatten(component, new FlattenerListener() {
            @Override
            public void pushStyle(@NotNull Style style) {
                styles.push(style.merge(styles.peek(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET));
            }

            @Override
            public void component(@NotNull String text) {
                if (!text.isEmpty()) runs.add(new Run(text, styles.peek()));
            }

            @Override
            public void popStyle(@NotNull Style style) {
                styles.pop();
            }
        });
        return runs;
    }

    private record Run(String text, Style style) {
    }
}