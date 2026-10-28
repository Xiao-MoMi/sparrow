package net.momirealms.sparrow.feature.ban.command;

import net.momirealms.sparrow.feature.ban.BanService;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.DurationParser;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class BanCommand extends AbstractBanCommand {

    public BanCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin) {
        super(manager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        for (Branch branch : Branch.values()) {
            manager.command(builder.literal(branch.name().toLowerCase(Locale.ROOT))
                    .required(branch == Branch.IP ? "ip" : "player", TokenParser.tokenParser(), branch == Branch.IP ? SuggestionProvider.noSuggestions() : this::suggestPlayers)
                    .optional("reason", StringParser.greedyFlagYieldingStringParser(), SuggestionProvider.blockingStrings((context, input) -> this.feature.getReasonNames()))
                    .flag(manager.flagBuilder("time").withComponent(DurationParser.durationParser()))
                    .flag(manager.flagBuilder("force"))
                    .flag(manager.flagBuilder("silent"))
                    .futureHandler(context -> this.execute(context, branch)));
        }
    }

    private CompletableFuture<Void> execute(CommandContext<CommandSender> context, Branch branch) {
        String input = context.get(branch == Branch.IP ? "ip" : "player");
        String reason = context.getOrDefault("reason", "");
        Duration time = context.flags().getValue("time", null);
        String operator = context.sender().getName();
        boolean force = context.flags().hasFlag("force");
        boolean silent = context.flags().hasFlag("silent");
        BanService service = this.feature.service();
        CompletableFuture<BanService.Result> operation = switch (branch) {
            case PLAYER -> service.banPlayer(input, reason, time, operator, force, silent);
            case PLAYER_IP -> service.banPlayerIp(input, reason, time, operator, force, silent);
            case IP -> service.banIp(input, reason, time, operator, force, silent);
        };
        return operation.thenAccept(result -> this.feedback(context, input, result));
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "ban";
    }
}