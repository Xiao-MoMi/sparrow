package net.momirealms.sparrow.plugin.command.panel;

import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;

public enum PanelTone {
    TITLE(0xFFE08A),
    LABEL(0x9EABB8),
    BODY(0xF2F4FF),
    INFO(0xC8E4E9),
    TIME(0xFFE3A3),
    SUCCESS(0xC9F2C7),
    DANGER(0xFFB6CB),
    ACTION(0xC7BCE7),
    MUTED(0x7D98BF);

    private final TextColor color;

    PanelTone(int color) {
        this.color = TextColor.color(color);
    }

    @NotNull
    public TextColor color() {
        return this.color;
    }
}