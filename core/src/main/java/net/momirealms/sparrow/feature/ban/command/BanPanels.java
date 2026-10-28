package net.momirealms.sparrow.feature.ban.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.momirealms.sparrow.feature.ban.BanQuery;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanTarget;
import net.momirealms.sparrow.feature.ban.BanTexts;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.panel.PanelText;
import net.momirealms.sparrow.plugin.command.panel.PanelTone;
import net.momirealms.sparrow.plugin.command.panel.TextPage;
import net.momirealms.sparrow.util.DurationUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class BanPanels {

    private BanPanels() {
    }

    static CommandPanel detail(BanRecord record) {
        long now = System.currentTimeMillis();
        String id = Long.toString(record.id());
        String history = record.player() == null ? "ip " + record.ip() : "player " + record.player();
        CommandPanel.Builder panel = CommandPanel.card()
                .title(PanelText.of(tr("info-title", Component.text(id))).copy(id))
                .status(status(record, now))
                .titleActions(
                        PanelButton.run(tr("unban"), "unban_id", id)
                                .tone(PanelTone.DANGER)
                                .disabledIf(!record.active(now), Component.translatable("command.ban.inactive")),
                        PanelButton.run(Component.translatable("command.panel.refresh"), "ban_info", id)
                )
                .actionColumn("history", tr("history"))
                .field(tr(record.player() == null ? "ip" : "player"), target(record), PanelButton.run(tr("history"), "ban_history", history).column("history"))
                .paragraph(tr("reason"), PanelText.of(BanTexts.reason(record.reason())))
                .field(tr("created"), PanelText.time(record.createdAt()))
                .field(tr("expires"), expiry(record, now))
                .field(tr("operator"), PanelText.of(tr("operator-value", Component.text(record.operatorName()), Component.text(record.server()))).tone(PanelTone.INFO));
        if (record.revokedAt() != 0) {
            panel.field(tr("revoked"), PanelText.of(tr("revoked-value", PanelText.time(record.revokedAt()).getComponent(), Component.text(record.revokedBy()))).tone(PanelTone.SUCCESS));
        }
        return panel.build();
    }

    static CommandPanel.Builder history(BanQuery query, @Nullable String playerInput) {
        Component scope = switch (query.target()) {
            case null -> tr("scope-all");
            case BanTarget.PlayerTarget player -> Component.text(playerInput == null ? player.uuid().toString() : playerInput);
            case BanTarget.IpTarget ignored -> tr("ip");
        };
        Component scopeDetails = switch (query.target()) {
            case null -> Component.translatable("command.ban-history.scope.all");
            case BanTarget.PlayerTarget player -> Component.text("UUID: " + player.uuid());
            case BanTarget.IpTarget ip -> Component.text(ip.range().toString());
        };
        CommandPanel.Builder panel = CommandPanel.list()
                .title(PanelText.of(tr("history-title", scope)).hover(scopeDetails))
                .actionColumn("details", tr("details"));
        List<Component> filters = new ArrayList<>();
        if (query.since() > 0) {
            filters.add(tr("within", Component.text(DurationUtils.format(query.now() - query.since()))).hoverEvent(PanelText.fullTime(query.since()).getComponent()));
        }
        if (query.operator() != null) {
            filters.add(tr("filter-operator", Component.text(query.operator())));
        }
        if (query.activeOnly()) {
            filters.add(tr("active-only"));
        }
        if (!filters.isEmpty()) {
            Component value = Component.join(JoinConfiguration.separator(Component.text(" · ")), filters);
            panel.summary(PanelText.of(tr("filters", value)).tone(PanelTone.MUTED));
        }
        return panel;
    }

    static CommandPanel history(CommandPanel.Builder panel, BanQuery query, TextPage<BanRecord> page, String arguments) {
        panel.status(PanelText.of(tr("total", Component.text(page.total()))).tone(PanelTone.MUTED));
        if (page.content().isEmpty()) {
            panel.entry(PanelText.of(Component.translatable("command.ban-history.empty")).tone(PanelTone.MUTED));
        }
        for (int i = 0, size = page.content().size(); i < size; i++) {
            BanRecord record = page.content().get(i);
            String id = Long.toString(record.id());
            Component details = details(record, query.now());
            panel.entry(
                    PanelText.of("#" + id).copy(id).hover(details),
                    status(record, query.now()),
                    target(record).hover(details),
                    PanelText.time(record.createdAt()),
                    PanelButton.run(tr("details"), "ban_info", id).column("details")
            );
        }
        return panel.pagination(page, "ban_history", number -> arguments + " --page " + number).build();
    }

    private static PanelText target(BanRecord record) {
        if (record.player() == null) {
            String ip = record.ip().toString();
            return PanelText.of(ip).copy(ip).tone(PanelTone.INFO);
        }
        String uuid = record.player().toString();
        return PanelText.of(BanTexts.target(record)).copy(uuid).hover(Component.text("UUID: " + uuid)).tone(PanelTone.INFO);
    }

    private static PanelText status(BanRecord record, long now) {
        if (record.revokedAt() != 0) return PanelText.of(tr("status-revoked")).tone(PanelTone.SUCCESS);
        return record.active(now) ? PanelText.of(tr("status-active")).tone(PanelTone.DANGER) : PanelText.of(tr("status-expired")).tone(PanelTone.MUTED);
    }

    private static PanelText expiry(BanRecord record, long now) {
        if (record.permanent()) return PanelText.of(Component.translatable("ban.expiry.permanent")).tone(PanelTone.DANGER);
        PanelText time = PanelText.time(record.expiresAt());
        return record.active(now)
                ? PanelText.of(tr("expiry-value", time.getComponent(), Component.text(DurationUtils.format(record.expiresAt() - now)))).tone(PanelTone.TIME)
                : time;
    }

    private static Component details(BanRecord record, long now) {
        List<Component> lines = new ArrayList<>();
        lines.add(tr("hover-title", Component.text(record.id()), status(record, now).getComponent()));
        lines.add(tr(record.player() == null ? "hover-ip" : "hover-player", BanTexts.target(record)));
        if (record.player() != null) {
            lines.add(Component.text("UUID: " + record.player(), PanelTone.MUTED.color()));
        }
        lines.add(tr("hover-reason", BanTexts.reason(record.reason())));
        lines.add(tr("hover-operator", Component.text(record.operatorName()), Component.text(record.server())));
        lines.add(tr("hover-created", PanelText.fullTime(record.createdAt()).getComponent()));
        lines.add(tr("hover-expires", record.permanent() ? Component.translatable("ban.expiry.permanent") : PanelText.fullTime(record.expiresAt()).getComponent()));
        if (record.revokedAt() != 0) {
            lines.add(tr("hover-revoked", PanelText.fullTime(record.revokedAt()).getComponent(), Component.text(record.revokedBy())));
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private static Component tr(String key, Component... arguments) {
        return Component.translatable("command.ban.panel." + key, arguments);
    }
}