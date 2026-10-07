package net.momirealms.sparrow.feature.warp.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.feature.warp.WarpFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class SetWarpCommand extends BukkitCommandFeature {
    private final WarpFeature feature;

    public SetWarpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull WarpFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    // 补全已有名称, 方便覆盖位置
    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class)
                .required("name", StringParser.greedyFlagYieldingStringParser(),
                        SuggestionProvider.blockingStrings((context, input) -> this.feature.suggest(context.sender(), input.remainingInput())))
                .handler(this::execute));
    }

    private void execute(CommandContext<Player> context) {
        Player player = context.sender();
        String name = context.get("name");
        this.save(context, name, WorldLocation.from(player.getLocation()));
    }

    private void save(CommandContext<Player> context, String name, WorldLocation location) {
        this.feature.service().set(name, ServerConfig.serverId(), location, context.sender().getUniqueId()).thenAccept(result -> {
            switch (result.status()) {
                case CREATED -> this.handleFeedback(context, MessageConstants.COMMAND_SET_WARP_CREATED, Component.text(result.warp().name()));
                case UPDATED -> this.handleFeedback(context, MessageConstants.COMMAND_SET_WARP_MOVED, Component.text(result.warp().name()));
                case DUPLICATE_NAME -> this.handleFeedback(context, MessageConstants.COMMAND_SET_WARP_EXISTS, Component.text(name));
                case NOT_FOUND -> this.handleFeedback(context, MessageConstants.COMMAND_WARP_UNKNOWN, Component.text(name));
                case INVALID_NAME -> this.handleFeedback(context, MessageConstants.COMMAND_WARP_INVALID_NAME, Component.text(name), Component.text(Warp.MAX_NAME_LENGTH));
                case DESCRIPTION_TOO_LONG -> throw new AssertionError();
            }
        }).exceptionally(error -> {
            this.plugin().logger().warn("Failed to save warp " + name, error);
            this.handleFeedback(context, MessageConstants.COMMAND_WARP_STORAGE_FAILED, Component.text(name));
            return null;
        });
    }

    @Override
    public String getFeatureID() {
        return "set-warp";
    }
}
