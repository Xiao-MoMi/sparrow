package net.momirealms.sparrow.feature.warp;

import net.kyori.adventure.text.Component;
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

import java.util.UUID;

public final class SetWarpCommand extends BukkitCommandFeature {
    private final WarpFeature feature;

    public SetWarpCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin, @NotNull WarpFeature feature) {
        super(commandManager, plugin);
        this.feature = feature;
    }

    // 补全已有名称, 方便覆盖位置
    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.senderType(Player.class)
                .required("name", StringParser.greedyFlagYieldingStringParser(),
                        SuggestionProvider.blockingStrings((context, input) -> this.feature.suggest(context.sender(), input.remainingInput())))
                .handler(this::execute);
    }

    private void execute(CommandContext<Player> context) {
        Player player = context.sender();
        String name = context.get("name");
        if (!this.feature.validName(name)) {
            this.handleFeedback(context, MessageConstants.COMMAND_WARP_INVALID_NAME, Component.text(name), Component.text(Warp.MAX_NAME_LENGTH));
            return;
        }
        this.plugin().scheduler().platform().run(() -> this.save(context, name, WorldLocation.from(player.getLocation())), () -> {}, player);
    }

    // 同名已存在时移到新位置并保留描述与创建信息, 名称的大小写以这次输入为准
    private void save(CommandContext<Player> context, String name, WorldLocation location) {
        long now = System.currentTimeMillis();
        Warp existing = this.feature.registry().get(name);
        if (existing != null && !this.feature.config().overwriteExisting()) {
            this.handleFeedback(context, MessageConstants.COMMAND_SET_WARP_EXISTS, Component.text(existing.name()));
            return;
        }
        Warp warp = existing == null
                ? new Warp(UUID.randomUUID(), name, "", ServerConfig.serverId(), location, context.sender().getUniqueId(), now, now)
                : new Warp(existing.id(), name, existing.description(), ServerConfig.serverId(), location, existing.creator(), existing.createdAt(), now);
        this.feature.registry().save(warp).thenAccept(saved -> {
            if (!saved) {
                this.handleFeedback(context, MessageConstants.COMMAND_SET_WARP_EXISTS, Component.text(name));
                return;
            }
            this.handleFeedback(context, existing == null ? MessageConstants.COMMAND_SET_WARP_CREATED : MessageConstants.COMMAND_SET_WARP_MOVED, Component.text(name));
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
