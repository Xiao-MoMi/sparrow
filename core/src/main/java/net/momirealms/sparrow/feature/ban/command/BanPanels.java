package net.momirealms.sparrow.feature.ban.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.ban.BanQuery;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanTarget;
import net.momirealms.sparrow.feature.ban.BanTexts;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.panel.TextPage;
import net.momirealms.sparrow.util.DateTimeUtils;
import org.bukkit.command.CommandSender;

final class BanPanels {

    private BanPanels() {
    }

    static void detail(CommandSender sender, BanRecord record) {
        long now = System.currentTimeMillis();
        String id = Long.toString(record.id());
        String history = record.player() == null ? "ip " + record.ip() : "player " + record.player();
        CommandPanel.Builder panel = CommandPanel.builder()
                .title(Component.translatable("command.ban-info.title", Component.text(id)))
                .copyField(Component.translatable("command.ban-info.id"), id)
                .field(Component.translatable("command.ban-info.type"), Component.translatable(record.player() == null ? "command.ban-info.type.ip" : "command.ban-info.type.player"))
                .field(Component.translatable("command.ban-info.target"), BanTexts.target(record));
        if (record.player() != null) {
            panel.copyField(Component.translatable("command.ban-info.uuid"), record.player().toString());
        } else {
            panel.copyField(Component.translatable("command.ban-info.ip"), record.ip().toString());
        }
        panel.field(Component.translatable("command.ban-info.reason"), BanTexts.reason(record.reason()))
                .field(Component.translatable("command.ban-info.status"), BanTexts.status(record, now))
                .field(Component.translatable("command.ban-info.operator"), record.operatorName())
                .field(Component.translatable("command.ban-info.server"), record.server())
                .field(Component.translatable("command.ban-info.created-at"), DateTimeUtils.fullTime(record.createdAt()))
                .field(Component.translatable("command.ban-info.expires-at"), BanTexts.expiry(record.expiresAt(), now));
        if (record.revokedAt() != 0) {
            panel.field(Component.translatable("command.ban-info.revoked-at"), DateTimeUtils.fullTime(record.revokedAt()))
                    .field(Component.translatable("command.ban-info.revoked-by"), record.revokedBy());
        }
        panel.buttons(
                        PanelButton.run(Component.translatable("command.ban.button.unban"), "unban_id", id)
                                .disabledIf(!record.active(now), Component.translatable("command.ban.inactive")),
                        PanelButton.run(Component.translatable("command.ban.button.history"), "ban_history", history),
                        PanelButton.run(Component.translatable("command.panel.refresh"), "ban_info", id)
                )
                .build()
                .send(sender);
    }

    static void history(CommandSender sender, BanQuery query, TextPage<BanRecord> page, String arguments) {
        Component scope = switch (query.target()) {
            case null -> Component.translatable("command.ban-history.scope.all");
            case BanTarget.PlayerTarget player -> Component.translatable("command.ban-history.scope.player", Component.text(player.uuid().toString()));
            case BanTarget.IpTarget ip -> Component.translatable("command.ban-history.scope.ip", Component.text(ip.range().toString()));
        };
        CommandPanel.Builder panel = CommandPanel.builder()
                .title(Component.translatable("command.ban-history.title"))
                .field(Component.translatable("command.ban-history.scope"), scope)
                .field(Component.translatable("command.ban-history.total"), Component.text(page.total()));
        if (query.operator() != null) {
            panel.field(Component.translatable("command.ban-history.operator"), query.operator());
        }
        if (query.since() > 0) {
            panel.field(Component.translatable("command.ban-history.since"), DateTimeUtils.fullTime(query.since()));
        }
        if (query.activeOnly()) {
            panel.field(Component.translatable("command.ban-history.status"), Component.translatable("ban.status.active"));
        }
        if (page.content().isEmpty()) {
            panel.field(Component.translatable("command.ban-history.records"), Component.translatable("command.ban-history.empty"));
        }
        for (int i = 0, size = page.content().size(); i < size; i++) {
            BanRecord record = page.content().get(i);
            String id = Long.toString(record.id());
            panel.field(
                    BanTexts.id(record.id()),
                    Component.translatable(
                            "command.ban-history.entry",
                            BanTexts.target(record),
                            BanTexts.status(record, query.now()),
                            Component.text(record.operatorName()),
                            Component.text(DateTimeUtils.fullTime(record.createdAt()))
                    )
            );
            panel.buttons(PanelButton.run(Component.translatable("command.ban.button.info", Component.text(id)), "ban_info", id));
        }
        panel.pagination(page, "ban_history", number -> arguments + " --page " + number).build().send(sender);
    }
}