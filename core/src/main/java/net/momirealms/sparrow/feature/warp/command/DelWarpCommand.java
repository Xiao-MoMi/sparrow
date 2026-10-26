package net.momirealms.sparrow.feature.warp.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.feature.warp.WarpFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

public final class DelWarpCommand extends BukkitCommandFeature {
    private final WarpFeature feature;

    public DelWarpCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin, @NotNull WarpFeature feature) {
        super(manager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required(
                "name",
                TokenParser.tokenParser(),
                SuggestionProvider.blockingStrings((context, input) -> this.feature.registry().complete(input.peekString(), warp -> true))
        ).handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String name = context.get("name");
        Warp warp = this.feature.registry().get(name);
        if (warp == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(name));
            return;
        }
        this.feature.service().delete(warp.id()).whenComplete((deleted, error) -> {
            if (error != null) {
                this.plugin().logger().warn("Failed to delete warp " + warp.name(), error);
                this.handleFeedback(context, MessageConstants.COMMAND_WARP_STORAGE_FAILED, Component.text(warp.name()));
                return;
            }
            this.handleFeedback(context, deleted ? MessageConstants.COMMAND_DEL_WARP_SUCCESS : MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(warp.name()));
        });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "del_warp";
    }
}