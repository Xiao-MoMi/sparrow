package net.momirealms.sparrow.feature.ban;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.util.DurationUtils;
import org.jetbrains.annotations.NotNull;

final class BanTexts {

    private BanTexts() {
    }

    // MessageConstants 中的 Builder 是共享实例, 先 build 再填参数
    @NotNull
    static TranslatableComponent translatable(@NotNull TranslatableComponent.Builder key, @NotNull ComponentLike... args) {
        return key.build().arguments(args);
    }

    // 带 # 前缀的处罚 ID, 点击复制
    @NotNull
    static Component id(@NotNull String id) {
        String text = BanRecord.ID_PREFIX + id;
        return Component.text(text).clickEvent(ClickEvent.copyToClipboard(text));
    }

    @NotNull
    static Component reason(@NotNull String reason) {
        return reason.isEmpty() ? MessageConstants.BAN_REASON_NONE.build() : Component.text(reason);
    }

    // 永久, 或到期时间加剩余时长
    @NotNull
    static Component expiry(long expiresAt, long now) {
        if (expiresAt == 0) return MessageConstants.BAN_EXPIRY_PERMANENT.build();
        return translatable(MessageConstants.BAN_EXPIRY_TEMPORARY, Component.text(CommandPanel.fullTime(expiresAt)), Component.text(DurationUtils.format(expiresAt - now)));
    }

    @NotNull
    static Component status(@NotNull BanRecord banRecord, long now) {
        if (banRecord.revokedAt() != 0) return MessageConstants.BAN_STATUS_REVOKED.build();
        return banRecord.active(now) ? MessageConstants.BAN_STATUS_ACTIVE.build() : MessageConstants.BAN_STATUS_EXPIRED.build();
    }
}
