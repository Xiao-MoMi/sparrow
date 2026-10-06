package net.momirealms.sparrow.feature.home;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

public final class DelHomeCommand extends AbstractHomeCommand {
    public DelHomeCommand(HomeFeature feature) {
        super(feature);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required("name", TokenParser.tokenParser(),
                        (context, input) -> this.feature.suggest(context.sender(), input.peekString(), false, this.commandConfig().getPermission()))
                .optional("player", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerManager().cluster())).handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String name = context.get("name");
        this.owner(sender, context.getOrDefault("player", null)).thenCompose(owner -> {
            if (owner.isEmpty()) return CompletableFuture.completedFuture(null);
            return this.feature.service().delete(owner.get().uuid(), name).thenAccept(deleted -> this.handleFeedback(context,
                    deleted ? MessageConstants.COMMAND_DEL_HOME_SUCCESS : MessageConstants.COMMAND_HOME_UNKNOWN,
                    Component.text(name), Component.text(owner.get().name())));
        }).exceptionally(error -> { this.failed(sender, error); return null; });
    }

    @Override
    public String getFeatureID() {
        return "del-home";
    }
}
