package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.LocationFlagParser;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportContext;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.processor.target.safe.FindSafeLoc;
import net.momirealms.sparrow.teleport.search.ChunkPolicy;
import net.momirealms.sparrow.teleport.search.PhysicalSafety;
import net.momirealms.sparrow.teleport.search.SearchPattern;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.WorldParser;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

public final class WorldCommand extends BukkitCommandFeature {
    private static final FindSafeLoc FIND_SAFE_LOCATION = FindSafeLoc.builder()
            .search(SearchPattern.exact(), SearchPattern.nearby().horizontalRadius(8).up(16).down(16).build())
            .rules(PhysicalSafety.builder().water(PhysicalSafety.WaterPolicy.HEAD_ABOVE_WATER).leafSupport(false).build())
            .chunks(ChunkPolicy.ALLOW_GENERATION)
            .maxChecks(10_000)
            .timeout(Duration.ofSeconds(3))
            .build();

    public WorldCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        Command.Builder<CommandSender> command = builder.required("world", WorldParser.worldParser())
                .flag(manager.flagBuilder("location").withComponent(LocationFlagParser.locationFlagParser()))
                .flag(manager.flagBuilder("unsafe"));
        manager.command(command.required("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .permission(this.otherPermission(command))
                .handler(this::execute));
        manager.command(command.handler(this::execute));
    }

    private void execute(CommandContext<CommandSender> context) {
        MultiplePlayerSelector selector = context.getOrDefault("targets", null);
        Collection<Player> targets;
        if (selector != null) {
            targets = selector.values();
        } else if (context.sender() instanceof Player player) {
            targets = List.of(player);
        } else {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        if (targets.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        World world = context.get("world");
        Location location = context.flags().getValue("location", null);
        if (location == null) {
            location = world.getSpawnLocation();
        }
        WorldLocation destination = new WorldLocation(world.getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        boolean unsafe = context.flags().hasFlag("unsafe");
        for (Player player : targets) {
            this.teleport(context, player, destination, unsafe);
        }
    }

    private void teleport(CommandContext<CommandSender> context, Player player, WorldLocation destination, boolean unsafe) {
        TeleportContext teleportContext = new TeleportContext();
        teleportContext.toNbt().putBoolean(TeleportContext.UNSAFE, unsafe);
        String name = player.getName();
        Teleport.builder()
                .server(ServerConfig.serverId())
                .position(TeleportPosition.ofPos(destination))
                .context(teleportContext)
                .targetProcessor(FIND_SAFE_LOCATION)
                .commit(player)
                .onResult(result -> {
                    switch (result) {
                        case TeleportResult.Success ignored -> this.handleFeedback(
                                context,
                                player == context.sender() ? MessageConstants.COMMAND_WORLD_SUCCESS_SELF : MessageConstants.COMMAND_WORLD_SUCCESS,
                                Component.text(name),
                                Component.text(destination.world())
                        );
                        case TeleportResult.Fail fail -> this.handleFeedback(context, MessageConstants.COMMAND_WORLD_FAILED, Component.text(name), fail.reason());
                        case TeleportResult.Aborted ignored -> this.handleFeedback(context, MessageConstants.COMMAND_WORLD_FAILED, Component.text(name), MessageConstants.TELEPORT_CANCELLED);
                    }
                })
                .onError(error -> {
                    this.plugin().logger().warn("Failed to teleport " + name + " to world " + destination.world(), error);
                    this.handleFeedback(context, MessageConstants.COMMAND_WORLD_FAILED, Component.text(name), MessageConstants.TELEPORT_FAILED);
                });
    }

    @Override
    public String getFeatureID() {
        return "world";
    }
}