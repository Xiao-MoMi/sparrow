package net.momirealms.sparrow.feature.mute;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.util.DateTimeUtils;
import net.momirealms.sparrow.util.DurationUtils;
import org.jetbrains.annotations.NotNull;

public final class MuteTexts {

    private MuteTexts() {
    }

    @NotNull
    public static TranslatableComponent describe(@NotNull String key, @NotNull MuteRecord record, long now) {
        return Component.translatable(
                key,
                Component.text(record.playerName()),
                record.reason().isEmpty() ? Component.translatable("mute.no-reason") : Component.text(record.reason()),
                Component.text(record.revokedBy() == null ? record.operatorName() : record.revokedBy()),
                Component.text(DurationUtils.format(Math.max(0, record.expiresAt() - now))),
                Component.text(DateTimeUtils.fullTime(record.expiresAt())),
                Component.text(record.id())
        );
    }
}