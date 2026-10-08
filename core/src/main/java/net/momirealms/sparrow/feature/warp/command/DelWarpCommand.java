package net.momirealms.sparrow.feature.warp.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.feature.warp.WarpFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class DelWarpCommand extends BukkitCommandFeature {
    private final WarpFeature feature;

    public DelWarpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull WarpFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.required(
                "name",
                StringParser.greedyFlagYieldingStringParser(),
                SuggestionProvider.blockingStrings((context, input) -> this.feature.suggest(context.sender(), input.remainingInput()))
                )
                .handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        String name = context.get("name");
        Warp warp = this.feature.registry().get(name);
        if (warp == null || !this.feature.visible(context.sender(), warp)) {
            this.handleFeedback(context, MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(name));
            return;
        }
        this.feature.service().delete(warp.id())
                .thenAccept(deleted -> {
                    // 同一时刻已被其他服务器删除
                    if (!deleted) {
                        this.handleFeedback(context, MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(warp.name()));
                        return;
                    }
                    this.handleFeedback(context, MessageConstants.COMMAND_DEL_WARP_SUCCESS, Component.text(warp.name()));
                })
                .exceptionally(error -> {
                    this.plugin().logger().warn("Failed to delete warp " + warp.name(), error);
                    this.handleFeedback(context, MessageConstants.COMMAND_WARP_STORAGE_FAILED, Component.text(warp.name()));
                    return null;
                });
    }

    @Override
    public String getFeatureID() {
        return "del-warp";
    }
}