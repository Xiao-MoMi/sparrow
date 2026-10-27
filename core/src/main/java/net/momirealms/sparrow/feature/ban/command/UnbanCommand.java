package net.momirealms.sparrow.feature.ban.command;

import net.momirealms.sparrow.feature.ban.BanFeature;
import net.momirealms.sparrow.feature.ban.BanService;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class UnbanCommand extends AbstractBanCommand {

    public UnbanCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin, @NotNull BanFeature feature) {
        super(manager, plugin, feature);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        for (Branch branch : Branch.values()) {
            manager.command(builder.literal(branch.name().toLowerCase(Locale.ROOT))
                    .required(branch == Branch.IP ? "ip" : "player", TokenParser.tokenParser(), branch == Branch.IP ? SuggestionProvider.noSuggestions() : this::suggestPlayers)
                    .flag(manager.flagBuilder("silent"))
                    .futureHandler(context -> this.execute(context, branch)));
        }
    }

    private CompletableFuture<Void> execute(CommandContext<CommandSender> context, Branch branch) {
        String input = context.get(branch == Branch.IP ? "ip" : "player");
        String operator = context.sender().getName();
        boolean silent = context.flags().hasFlag("silent");
        BanService service = this.feature.service();
        CompletableFuture<BanService.Result> operation = switch (branch) {
            case PLAYER -> service.unbanPlayer(input, operator, silent);
            case PLAYER_IP -> service.unbanPlayerIp(input, operator, silent);
            case IP -> service.unbanIp(input, operator, silent);
        };
        return operation.thenAccept(result -> this.feedback(context, input, result));
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "unban";
    }
}