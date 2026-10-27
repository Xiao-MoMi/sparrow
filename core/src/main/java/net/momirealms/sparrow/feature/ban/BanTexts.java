package net.momirealms.sparrow.feature.ban;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.util.DateTimeUtils;
import net.momirealms.sparrow.util.DurationUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class BanTexts {

    private BanTexts() {
    }

    @NotNull
    public static TranslatableComponent kickScreen(@NotNull BanRecord record, @NotNull String playerName, long now) {
        TranslatableComponent template = record.player() != null
                ? (record.permanent() ? MessageConstants.BAN_KICK_PLAYER_PERMANENT : MessageConstants.BAN_KICK_PLAYER_TEMPORARY)
                : (record.permanent() ? MessageConstants.BAN_KICK_IP_PERMANENT : MessageConstants.BAN_KICK_IP_TEMPORARY);
        return template.arguments(
                reason(record.reason()),
                Component.text(record.operatorName()),
                expiry(record.expiresAt(), now),
                Component.text(record.id()),
                Component.text(playerName),
                Component.text(DateTimeUtils.fullTime(record.createdAt()))
        );
    }

    @NotNull
    public static TranslatableComponent notification(@NotNull BanRecord record, long now) {
        Component target = record.player() == null
                ? Component.text(record.ip().toString())
                : Component.text(record.playerName() == null ? record.player().toString() : record.playerName());
        if (record.revokedAt() != 0) {
            return MessageConstants.BAN_NOTIFY_UNBAN.arguments(target, Component.text(record.revokedBy()), id(record.id()));
        }
        TranslatableComponent template = record.permanent() ? MessageConstants.BAN_NOTIFY_BAN_PERMANENT : MessageConstants.BAN_NOTIFY_BAN_TEMPORARY;
        return template.arguments(
                target,
                Component.text(record.operatorName()),
                reason(record.reason()),
                expiry(record.expiresAt(), now),
                id(record.id()),
                Component.text(DateTimeUtils.fullTime(record.createdAt()))
        );
    }

    @NotNull
    public static Component id(long id) {
        String value = Long.toString(id);
        return Component.text(value).clickEvent(ClickEvent.copyToClipboard(value)).hoverEvent(MessageConstants.BAN_ID_COPY);
    }

    @NotNull
    public static Component reason(@NotNull String reason) {
        return reason.isEmpty() ? MessageConstants.BAN_REASON_NONE : Component.text(reason);
    }

    @NotNull
    public static Component expiry(long expiresAt, long now) {
        if (expiresAt == 0) return MessageConstants.BAN_EXPIRY_PERMANENT;
        return MessageConstants.BAN_EXPIRY_TEMPORARY.arguments(
                Component.text(DateTimeUtils.fullTime(expiresAt)),
                Component.text(DurationUtils.format(expiresAt - now))
        );
    }
}