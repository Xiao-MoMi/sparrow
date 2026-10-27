package net.momirealms.sparrow.feature.home.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.home.HomeFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public final class DelHomeCommand extends AbstractHomeCommand {

    public DelHomeCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin, @NotNull HomeFeature feature) {
        super(manager, plugin, feature);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("name", TokenParser.tokenParser(), this::suggest).futureHandler(this::execute));
    }

    private CompletableFuture<Void> execute(CommandContext<CommandSender> context) {
        return this.withTarget(context, (owner, name) -> this.feature.service().delete(owner.uniqueId(), name)
                .thenAccept(result -> this.saved(context, owner, result, name, MessageConstants.COMMAND_DEL_HOME_SUCCESS, Component.text(name), Component.text(owner.name()))));
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "del_home";
    }
}