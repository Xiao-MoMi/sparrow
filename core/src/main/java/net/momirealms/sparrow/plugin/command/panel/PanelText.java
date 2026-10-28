package net.momirealms.sparrow.plugin.command.panel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class PanelText {
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FULL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Component content;
    private final @Nullable Component hover;
    private final @Nullable String copy;
    private final @Nullable PanelTone tone;
    private final boolean wholeWords;

    private PanelText(Component content, @Nullable Component hover, @Nullable String copy, @Nullable PanelTone tone, boolean wholeWords) {
        this.content = content;
        this.hover = hover;
        this.copy = copy;
        this.tone = tone;
        this.wholeWords = wholeWords;
    }

    @NotNull
    public static PanelText of(@NotNull String content) {
        return of(Component.text(content));
    }

    @NotNull
    public static PanelText of(@NotNull Component content) {
        return new PanelText(content, null, null, null, false);
    }

    @NotNull
    public static PanelText time(long millis) {
        Instant instant = Instant.ofEpochMilli(millis);
        return of(SHORT_TIME.format(instant)).hover(Component.text(FULL_TIME.format(instant))).tone(PanelTone.TIME);
    }

    @NotNull
    public static PanelText fullTime(long millis) {
        return of(FULL_TIME.format(Instant.ofEpochMilli(millis))).tone(PanelTone.TIME);
    }

    @NotNull
    public static PanelText coordinates(double x, double y, double z) {
        String text = String.format(Locale.ROOT, "%.2f %.2f %.2f", normalized(x), normalized(y), normalized(z));
        return new PanelText(Component.text(text), null, text, PanelTone.INFO, true);
    }

    @NotNull
    public static PanelText coordinates(double x, double y, double z, float yaw, float pitch) {
        String text = String.format(Locale.ROOT, "%.2f %.2f %.2f %.2f %.2f", normalized(x), normalized(y), normalized(z), normalized(yaw), normalized(pitch));
        return new PanelText(Component.text(text), null, text, PanelTone.INFO, true);
    }

    @NotNull
    public static PanelText rotation(float yaw, float pitch) {
        String text = String.format(Locale.ROOT, "%.2f %.2f", normalized(yaw), normalized(pitch));
        return new PanelText(Component.text(text), null, text, PanelTone.INFO, true);
    }

    private static double normalized(double value) {
        return Math.abs(value) < 0.005 ? 0 : value;
    }

    @NotNull
    public static PanelText location(@NotNull String server, @NotNull WorldLocation location) {
        PanelText position = coordinates(location.x(), location.y(), location.z());
        PanelText rotation = rotation(location.yaw(), location.pitch());
        Component value = Component.translatable("command.panel.location", Component.text(server), Component.text(location.world()));
        Component details = Component.translatable("command.panel.location-hover", Component.text(server), Component.text(location.world()), position.content, rotation.content);
        return new PanelText(value, details, position.copy, PanelTone.INFO, false);
    }

    @NotNull
    public PanelText hover(@NotNull Component hover) {
        return new PanelText(this.content, hover, this.copy, this.tone, this.wholeWords);
    }

    @NotNull
    public PanelText copy(@NotNull String value) {
        return new PanelText(this.content, this.hover, value, this.tone, this.wholeWords);
    }

    @NotNull
    public PanelText tone(@NotNull PanelTone tone) {
        return new PanelText(this.content, this.hover, this.copy, tone, this.wholeWords);
    }

    @NotNull
    public Component getComponent() {
        Component result = this.tone == null ? this.content : this.content.colorIfAbsent(this.tone.color());
        Component tooltip = this.hover;
        if (this.copy != null) {
            result = PanelTypography.withoutEvents(result).clickEvent(ClickEvent.copyToClipboard(this.copy));
            Component hint = Component.translatable("command.panel.copy").color(PanelTone.MUTED.color());
            tooltip = tooltip == null
                    ? hint.append(Component.newline()).append(Component.text(this.copy, PanelTone.BODY.color()))
                    : tooltip.append(Component.newline()).append(hint);
        }
        return tooltip == null ? result : result.hoverEvent(tooltip);
    }

    Component getConsoleComponent() {
        Component result = this.tone == null ? this.content : this.content.colorIfAbsent(this.tone.color());
        if (this.hover != null) {
            result = result.append(Component.text(" (", PanelTone.MUTED.color())).append(this.hover).append(Component.text(")", PanelTone.MUTED.color()));
        } else if (this.copy != null && !this.copy.equals(PlainTextComponentSerializer.plainText().serialize(this.content))) {
            result = result.append(Component.text(" [" + this.copy + "]", PanelTone.MUTED.color()));
        }
        return result;
    }

    boolean wholeWords() {
        return this.wholeWords;
    }
}