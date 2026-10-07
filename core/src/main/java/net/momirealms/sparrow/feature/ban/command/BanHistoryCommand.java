package net.momirealms.sparrow.feature.ban.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.momirealms.sparrow.feature.ban.*;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.panel.TextPage;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import net.momirealms.sparrow.plugin.command.parser.DurationParser;
import net.momirealms.sparrow.plugin.command.parser.OptionalWordParser;
import net.momirealms.sparrow.util.CharacterUtils;
import net.momirealms.sparrow.util.DateTimeUtils;
import net.momirealms.sparrow.util.DurationUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class BanHistoryCommand extends BukkitCommandFeature {
    private static final int PAGE_SIZE = 8;
    private static final int TARGET_LENGTH = 20;    // 行内对象的码点上限, 完整信息在悬浮中
    private static final int OPERATOR_LENGTH = 16;
    private static final String STATUS_SYMBOL = "●";

    private final BanFeature feature;

    public BanHistoryCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull BanFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        // 只写筛选选项时对象留空, flag 交给后面的解析器
        manager.command(builder.optional("target", OptionalWordParser.optionalWordParser(), (context, input) -> CompletableFuture.completedFuture(this.plugin().playerManager().cluster().suggest(input.peekString())))
                .flag(manager.flagBuilder("operator").withAliases("o").withComponent(ClusterPlayerParser.clusterPlayerParser(this.plugin().playerManager().cluster())))
                .flag(manager.flagBuilder("within").withAliases("w").withComponent(DurationParser.durationParser()))
                .flag(manager.flagBuilder("active").withAliases("a"))
                .flag(manager.flagBuilder("page").withAliases("p").withComponent(IntegerParser.integerParser(1)))
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String input = context.<Optional<String>>getOrDefault("target", Optional.empty()).orElse(null);
        Filters filters = new Filters(context.flags().getValue("operator", null), context.flags().getValue("within", null), context.flags().hasFlag("active"));
        int page = context.flags().getValue("page", 1);
        CompletableFuture<Optional<BanTarget>> resolved;
        if (input == null) {
            resolved = CompletableFuture.completedFuture(Optional.empty());
        } else {
            try {
                resolved = this.feature.resolveTarget(input);
            } catch (IllegalArgumentException exception) {
                this.handleFeedback(sender, MessageConstants.COMMAND_INVALID_TARGET, Component.text(input));
                return;
            }
        }
        resolved.thenCompose(found -> {
            if (input != null && found.isEmpty()) {
                this.handleFeedback(sender, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(input));
                return CompletableFuture.completedFuture(null);
            }
            BanTarget target = found.orElse(null);
            long now = System.currentTimeMillis();
            long since = filters.within() == null ? 0 : now - filters.within().toMillis();
            BanQuery query = new BanQuery(target, filters.operator(), since, filters.active(), now);
            return TextPage.load(() -> this.feature.store().countBans(query), (offset, limit) -> this.feature.store().listBans(query, offset, limit), page - 1, PAGE_SIZE)
                    .thenAccept(result -> this.render(sender, input, target, filters, result, now));
        }).exceptionally(error -> {
            this.plugin().logger().warn("Failed to list bans of " + input, error);
            this.handleFeedback(sender, MessageConstants.COMMAND_DATABASE_FAILED);
            return null;
        });
    }

    private void render(CommandSender sender, @Nullable String input, @Nullable BanTarget target, Filters filters, TextPage<BanRecord> page, long now) {
        boolean player = sender instanceof Player;
        CommandPanel panel = new CommandPanel(this.commandManager(), sender).header(this.title(target, filters), page);
        List<BanRecord> records = page.content();
        int size = records.size();
        // 按本页各行可变文本的宽度补白, 让解封按钮纵向对齐
        int alignedWidth = 0;
        if (player) {
            int[] widths = new int[size];
            for (int i = 0; i < size; i++) {
                widths[i] = rowWidth(records.get(i));
            }
            alignedWidth = CharacterUtils.alignedChatWidth(widths);
        }
        for (int i = 0; i < size; i++) {
            BanRecord record = records.get(i);
            Component padding = player ? CharacterUtils.chatPadding(alignedWidth - rowWidth(record)) : Component.empty();
            panel.line(this.row(panel, sender, record, now, padding));
        }
        if (size == 0) {
            panel.empty();
        }
        // 翻页链接保留当前的对象和筛选条件
        String prefix = input == null ? "" : input + " ";
        String suffix = filters.arguments();
        panel.navigation(this.getFeatureID(), page, index -> prefix + "--page " + index + suffix).send();
    }

    private Component title(@Nullable BanTarget target, Filters filters) {
        Component title = target == null
                ? MessageConstants.COMMAND_BAN_HISTORY_TITLE_ALL
                : MessageConstants.COMMAND_BAN_HISTORY_TITLE.arguments(Component.text(target.display()));
        if (filters.operator() != null) {
            title = title.append(MessageConstants.COMMAND_BAN_HISTORY_FILTER_OPERATOR
                    .arguments(Component.text(filters.operator())));
        }
        if (filters.within() != null) {
            title = title.append(MessageConstants.COMMAND_BAN_HISTORY_FILTER_WITHIN
                    .arguments(Component.text(DurationUtils.format(filters.within().toMillis()))));
        }
        if (filters.active()) {
            title = title.append(MessageConstants.COMMAND_BAN_HISTORY_FILTER_ACTIVE);
        }
        return title;
    }

    // 玩家看到截断后的对象和执行人, 完整信息在 ID 的悬浮中; 控制台直接输出完整文本
    private Component row(CommandPanel panel, CommandSender sender, BanRecord record, long now, Component padding) {
        boolean player = sender instanceof Player;
        Component id = BanTexts.id(record.id());
        Component time = Component.text(player ? DateTimeUtils.shortTime(record.createdAt()) : DateTimeUtils.fullTime(record.createdAt()));
        Component target = Component.text(player ? CharacterUtils.truncate(record.display(), TARGET_LENGTH) : record.display());
        Component operator = Component.text(player ? CharacterUtils.truncate(record.operatorName(), OPERATOR_LENGTH) : record.operatorName());
        Component status = BanTexts.status(record, now);
        if (player) {
            id = id.hoverEvent(this.details(record, now));
            status = Component.text(STATUS_SYMBOL, statusColor(record, now)).hoverEvent(status);
            String copy = record.player() != null ? record.player().toString() : String.valueOf(record.ip());
            target = target.hoverEvent(this.targetDetails(record)).clickEvent(ClickEvent.copyToClipboard(copy));
        }
        Component unban = panel.suggest(CommandPanel.label("unban"), "unban", BanRecord.ID_PREFIX + record.id()).style(PanelButton.Style.DANGER)
                .disabled(record.active(now) ? null : Component.translatable("command.panel.inactive"))
                .build();
        return MessageConstants.COMMAND_BAN_HISTORY_ROW
                .arguments(id, time, status, target, operator.append(padding), unban);
    }

    private Component details(BanRecord record, long now) {
        Component revoked = record.revokedAt() == 0
                ? BanTexts.status(record, now)
                : MessageConstants.COMMAND_BAN_HISTORY_REVOKED
                .arguments(Component.text(String.valueOf(record.revokedBy())), Component.text(DateTimeUtils.fullTime(record.revokedAt())));
        return MessageConstants.COMMAND_BAN_HISTORY_HOVER
                .arguments(
                        Component.text(BanRecord.ID_PREFIX + record.id()),
                        this.targetDetails(record),
                        BanTexts.reason(record.reason()),
                        Component.text(record.operatorName()),
                        Component.text(record.server()),
                        Component.text(DateTimeUtils.fullTime(record.createdAt())),
                        BanTexts.expiry(record.expiresAt(), now),
                        revoked
                );
    }

    // 玩家名 (UUID) 与 IP, 按记录实际包含的部分组合
    private Component targetDetails(BanRecord record) {
        Component account = record.player() == null ? Component.empty() : Component.text(record.playerName() + " (" + record.player() + ")");
        Component ip = record.ip() == null ? Component.empty() : Component.text(record.ip().toString());
        if (record.player() != null && record.ip() != null) return account.append(Component.newline()).append(ip);
        return record.player() != null ? account : ip;
    }

    // 影响按钮位置的可变文本宽度, 单位为聊天字体像素
    private static int rowWidth(BanRecord record) {
        return CharacterUtils.chatWidth(BanRecord.ID_PREFIX + record.id()) + CharacterUtils.chatWidth(DateTimeUtils.shortTime(record.createdAt()))
                + CharacterUtils.chatWidth(CharacterUtils.truncate(record.display(), TARGET_LENGTH)) + CharacterUtils.chatWidth(CharacterUtils.truncate(record.operatorName(), OPERATOR_LENGTH));
    }

    private static NamedTextColor statusColor(BanRecord record, long now) {
        if (record.revokedAt() != 0) return NamedTextColor.YELLOW;
        return record.active(now) ? NamedTextColor.RED : NamedTextColor.GRAY;
    }

    @Override
    public String getFeatureID() {
        return "ban-history";
    }

    /**
     * 命令行中的筛选选项, 翻页时原样带回.
     *
     * @param operator 执行人名字, 为 null 时不限
     * @param within 只看最近这段时间内的封禁, 为 null 时不限
     * @param active 只看仍生效的封禁
     */
    private record Filters(@Nullable String operator, @Nullable Duration within, boolean active) {

        // 拼在翻页命令末尾的选项
        String arguments() {
            return (this.operator == null ? "" : " --operator " + this.operator)
                    + (this.within == null ? "" : " --within " + DurationUtils.format(this.within.toMillis()))
                    + (this.active ? " --active" : "");
        }
    }
}
