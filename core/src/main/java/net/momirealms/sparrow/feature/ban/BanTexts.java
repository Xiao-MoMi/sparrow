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
    public static TranslatableComponent kickScreen(boolean account,
                                                   @NotNull String playerName,
                                                   @NotNull String id,
                                                   @NotNull String reason,
                                                   @NotNull String operatorName,
                                                   long createdAt,
                                                   long expiresAt,
                                                   long now) {
        TranslatableComponent template = account
                ? (expiresAt == 0 ? MessageConstants.BAN_KICK_PLAYER_PERMANENT : MessageConstants.BAN_KICK_PLAYER_TEMPORARY)
                : (expiresAt == 0 ? MessageConstants.BAN_KICK_IP_PERMANENT : MessageConstants.BAN_KICK_IP_TEMPORARY);
        return template.arguments(
                reason(reason),
                Component.text(operatorName),
                expiry(expiresAt, now),
                Component.text(BanRecord.ID_PREFIX + id),
                Component.text(playerName),
                Component.text(DateTimeUtils.fullTime(createdAt))
        );
    }

    @NotNull
    public static TranslatableComponent notification(@NotNull BanMessage message, long now) {
        Component target = Component.text(message.display());
        Component operator = Component.text(message.operatorName());
        if (!message.banned()) {
            return MessageConstants.BAN_NOTIFY_UNBAN.arguments(target, operator);
        }
        TranslatableComponent template = message.expiresAt() == 0
                        ? MessageConstants.BAN_NOTIFY_BAN_PERMANENT
                        : MessageConstants.BAN_NOTIFY_BAN_TEMPORARY;
        return template.arguments(
                target,
                operator,
                reason(message.reason()),
                expiry(message.expiresAt(), now),
                id(message.banId()),
                Component.text(DateTimeUtils.fullTime(message.createdAt()))
        );
    }

    // 带 # 前缀的处罚 ID, 点击复制
    @NotNull
    public static Component id(@NotNull String id) {
        String text = BanRecord.ID_PREFIX + id;
        return Component.text(text).clickEvent(ClickEvent.copyToClipboard(text)).hoverEvent(MessageConstants.BAN_ID_COPY);
    }

    @NotNull
    public static Component reason(@NotNull String reason) {
        return reason.isEmpty() ? MessageConstants.BAN_REASON_NONE : Component.text(reason);
    }

    // 永久, 或到期时间加剩余时长
    @NotNull
    public static Component expiry(long expiresAt, long now) {
        if (expiresAt == 0) {
            return MessageConstants.BAN_EXPIRY_PERMANENT;
        }
        return MessageConstants.BAN_EXPIRY_TEMPORARY
                .arguments(Component.text(DateTimeUtils.fullTime(expiresAt)), Component.text(DurationUtils.format(expiresAt - now)));
    }

    @NotNull
    public static Component status(@NotNull BanRecord record, long now) {
        if (record.revokedAt() != 0) {
            return MessageConstants.BAN_STATUS_REVOKED;
        }
        return record.active(now)
                ? MessageConstants.BAN_STATUS_ACTIVE
                : MessageConstants.BAN_STATUS_EXPIRED;
    }
}