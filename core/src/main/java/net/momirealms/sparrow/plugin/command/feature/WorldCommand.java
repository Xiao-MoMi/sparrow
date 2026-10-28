package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BatchCommandFeedback;
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
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.permission.PermissionResult;
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
        Permission otherPermission = this.otherPermission(builder);
        manager.command(builder.required("world", WorldParser.worldParser())
                .optional("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .flag(manager.flagBuilder("location").withComponent(LocationFlagParser.locationFlagParser()))
                .flag(manager.flagBuilder("unsafe"))
                .handler(context -> {
                    if (context.contains("targets")) {
                        PermissionResult permission = manager.testPermission(context.sender(), otherPermission);
                        if (!permission.allowed()) {
                            throw new NoPermissionException(permission, context.sender(), List.copyOf(context.command().components()));
                        }
                    }
                    this.execute(context);
                }));
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
        BatchCommandFeedback feedback = new BatchCommandFeedback(this, context, targets.size());
        for (Player player : targets) {
            this.teleport(context, player, destination, unsafe, feedback);
        }
    }

    private void teleport(CommandContext<CommandSender> context, Player player, WorldLocation destination, boolean unsafe, BatchCommandFeedback feedback) {
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
                        case TeleportResult.Success ignored -> feedback.success(
                                player == context.sender() ? MessageConstants.COMMAND_WORLD_SUCCESS_SELF : MessageConstants.COMMAND_WORLD_SUCCESS,
                                Component.text(name),
                                Component.text(destination.world())
                        );
                        case TeleportResult.Fail fail -> feedback.failure(MessageConstants.COMMAND_WORLD_FAILED, Component.text(name), fail.reason());
                        case TeleportResult.Aborted ignored -> feedback.failure(MessageConstants.COMMAND_WORLD_FAILED, Component.text(name), MessageConstants.TELEPORT_CANCELLED);
                    }
                })
                .onError(error -> {
                    this.plugin().logger().warn("Failed to teleport " + name + " to world " + destination.world(), error);
                    feedback.failure(MessageConstants.COMMAND_WORLD_FAILED, Component.text(name), MessageConstants.TELEPORT_FAILED);
                });
    }

    @Override
    public String getFeatureID() {
        return "world";
    }
}