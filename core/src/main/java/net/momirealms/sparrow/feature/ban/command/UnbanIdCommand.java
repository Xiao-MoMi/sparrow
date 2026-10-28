package net.momirealms.sparrow.feature.ban.command;

import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.LongParser;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class UnbanIdCommand extends AbstractBanCommand {

    public UnbanIdCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin) {
        super(manager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("id", LongParser.longParser(1))
                .flag(manager.flagBuilder("silent"))
                .futureHandler(this::execute));
    }

    private CompletableFuture<Void> execute(CommandContext<CommandSender> context) {
        long id = context.get("id");
        return this.feature.service().unbanId(id, context.sender().getName(), context.flags().hasFlag("silent"))
                .thenAccept(result -> this.feedback(context, Long.toString(id), result));
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "unban_id";
    }
}