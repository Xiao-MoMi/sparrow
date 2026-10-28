package net.momirealms.sparrow.feature.warp.command;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.warp.Warp;
import net.momirealms.sparrow.feature.warp.WarpFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

public final class SetWarpCommand extends BukkitCommandFeature {
    private final WarpFeature feature = SparrowPlugin.instance().featureManager().feature(WarpFeature.ID, WarpFeature.class);

    public SetWarpCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin) {
        super(manager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.senderType(Player.class).required("name", TokenParser.tokenParser()).handler(this::execute));
    }

    private void execute(CommandContext<Player> context) {
        String name = context.get("name");
        this.feature.service().create(name, ServerConfig.serverId(), WorldLocation.from(context.sender().getLocation()), context.sender().getUniqueId())
                .whenComplete((result, error) -> {
                    if (error != null) {
                        this.plugin().logger().warn("Failed to create warp " + name, error);
                        this.handleFeedback(context, MessageConstants.COMMAND_WARP_STORAGE_FAILED, Component.text(name));
                        return;
                    }
                    switch (result.status()) {
                        case CREATED -> this.handleFeedback(context, MessageConstants.COMMAND_SET_WARP_CREATED, Component.text(name));
                        case DUPLICATE_NAME -> this.handleFeedback(context, MessageConstants.COMMAND_WARP_EXISTS, Component.text(name));
                        case INVALID_NAME -> this.handleFeedback(context, MessageConstants.COMMAND_WARP_INVALID_NAME, Component.text(name), Component.text(Warp.MAX_NAME_LENGTH));
                        case NOT_FOUND, UPDATED -> throw new IllegalStateException("Unexpected create result: " + result.status());
                    }
                });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "set_warp";
    }
}