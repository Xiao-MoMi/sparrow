package net.momirealms.sparrow.feature.ban.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.ban.BanFeature;
import net.momirealms.sparrow.feature.ban.BanQuery;
import net.momirealms.sparrow.feature.ban.BanTarget;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.TextPage;
import net.momirealms.sparrow.plugin.command.parser.DurationParser;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import net.momirealms.sparrow.util.IpRange;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class BanHistoryCommand extends AbstractBanCommand {
    private static final int PAGE_SIZE = 8;

    public BanHistoryCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin, @NotNull BanFeature feature) {
        super(manager, plugin, feature);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(this.filters(manager, builder.literal("all")).futureHandler(context -> this.query(context, null)));
        manager.command(this.filters(manager, builder.literal("player").required("player", TokenParser.tokenParser(), this::suggestPlayers)).futureHandler(this::player));
        manager.command(this.filters(manager, builder.literal("ip").required("ip", TokenParser.tokenParser())).futureHandler(this::ip));
    }

    private Command.Builder<CommandSender> filters(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.flag(manager.flagBuilder("page").withComponent(IntegerParser.integerParser(1)))
                .flag(manager.flagBuilder("active"))
                .flag(manager.flagBuilder("within").withComponent(DurationParser.durationParser()))
                .flag(manager.flagBuilder("operator").withComponent(StringParser.quotedStringParser()));
    }

    private CompletableFuture<Void> player(CommandContext<CommandSender> context) {
        String input = context.get("player");
        return this.feature.service().resolvePlayerTarget(input).thenCompose(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_BAN_UNKNOWN_PLAYER, Component.text(input));
                return CompletableFuture.completedFuture(null);
            }
            return this.query(context, found.get());
        });
    }

    private CompletableFuture<Void> ip(CommandContext<CommandSender> context) {
        String input = context.get("ip");
        IpRange range;
        try {
            range = IpRange.parse(input);
        } catch (IllegalArgumentException exception) {
            this.handleFeedback(context, MessageConstants.COMMAND_BAN_INVALID_IP, Component.text(input));
            return CompletableFuture.completedFuture(null);
        }
        return this.query(context, new BanTarget.IpTarget(range));
    }

    private CompletableFuture<Void> query(CommandContext<CommandSender> context, @Nullable BanTarget target) {
        String operator = context.flags().getValue("operator", null);
        Duration within = context.flags().getValue("within", null);
        boolean active = context.flags().hasFlag("active");
        long now = System.currentTimeMillis();
        long since = within == null ? 0 : Math.max(0, now - within.toMillis());
        BanQuery query = new BanQuery(target, operator, since, active, now);
        String scope = switch (target) {
            case null -> "all";
            case BanTarget.PlayerTarget player -> "player " + player.uuid();
            case BanTarget.IpTarget ip -> "ip " + ip.range();
        };
        String arguments = scope
                + (operator == null ? "" : " --operator \"" + operator.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                + (within == null ? "" : " --within " + within.toMillis() + "ms")
                + (active ? " --active" : "");
        int requestedPage = context.flags().getValue("page", 1);
        return this.feature.service().count(query).thenCompose(total -> {
            int pages = total == 0 ? 1 : Math.toIntExact((total - 1) / PAGE_SIZE + 1);
            int page = Math.min(requestedPage, pages);
            int offset = Math.multiplyExact(page - 1, PAGE_SIZE);
            return this.feature.service().list(query, offset, PAGE_SIZE)
                    .thenAccept(records -> BanPanels.history(context.sender(), query, new TextPage<>(page - 1, PAGE_SIZE, total, records), arguments));
        });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "ban_history";
    }
}