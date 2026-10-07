package net.momirealms.sparrow.feature.ban;

import net.kyori.adventure.text.Component;
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

    // 带 # 前缀的处罚 ID, 点击复制
    @NotNull
    static Component id(@NotNull String id) {
        String text = BanRecord.ID_PREFIX + id;
        return Component.text(text).clickEvent(ClickEvent.copyToClipboard(text));
    }

    @NotNull
    static Component reason(@NotNull String reason) {
        return reason.isEmpty() ? MessageConstants.BAN_REASON_NONE : Component.text(reason);
    }

    // 永久, 或到期时间加剩余时长
    @NotNull
    static Component expiry(long expiresAt, long now) {
        if (expiresAt == 0) return MessageConstants.BAN_EXPIRY_PERMANENT;
        return MessageConstants.BAN_EXPIRY_TEMPORARY
                .arguments(Component.text(DateTimeUtils.fullTime(expiresAt)), Component.text(DurationUtils.format(expiresAt - now)));
    }

    @NotNull
    static Component status(@NotNull BanRecord record, long now) {
        if (record.revokedAt() != 0) return MessageConstants.BAN_STATUS_REVOKED;
        return record.active(now)
                ? MessageConstants.BAN_STATUS_ACTIVE
                : MessageConstants.BAN_STATUS_EXPIRED;
    }
}
