package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportResult;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class RespawnCommand extends BukkitCommandFeature {

    public RespawnCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", PlayerParser.playerParser())
                .permission(this.otherPermission(builder))
                .handler(this::execute));
        manager.command(builder.handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        Player player = context.getOrDefault("player", context.sender() instanceof Player sender ? sender : null);
        if (player == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        this.plugin().scheduler().platform().run(() -> this.resolve(context, player), () -> {}, player);
    }

    private void resolve(@NotNull CommandContext<CommandSender> context, @NotNull Player player) {
        ServerPlayer.RespawnConfig respawn = ((CraftPlayer) player).getHandle().getRespawnConfig();
        if (respawn == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_RESPAWN_NO_LOCATION, Component.text(player.getName()));
            return;
        }
        ServerLevel level = MinecraftServer.getServer().getLevel(respawn.respawnData().dimension());
        if (level == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_RESPAWN_NO_LOCATION, Component.text(player.getName()));
            return;
        }
        BlockPos position = respawn.respawnData().pos();
        World world = level.getWorld();
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        // 原版床与重生锚验证会读取相邻区块, 在目标区域执行前一并加载.
        List<CompletableFuture<?>> chunks = new ArrayList<>(9);
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
                chunks.add(world.getChunkAtAsync(x, z));
            }
        }
        CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new))
                .thenRunAsync(
                        () -> this.teleport(context, player, level, respawn),
                        task -> this.plugin().scheduler().platform().run(task, world, chunkX, chunkZ)
                )
                .exceptionally(error -> {
                    this.plugin().logger().warn("Failed to resolve respawn location for " + player.getName(), error);
                    this.handleFeedback(context, MessageConstants.COMMAND_RESPAWN_FAILED, Component.text(player.getName()), MessageConstants.TELEPORT_FAILED);
                    return null;
                });
    }

    private void teleport(@NotNull CommandContext<CommandSender> context, @NotNull Player player, @NotNull ServerLevel level, @NotNull ServerPlayer.RespawnConfig respawn) {
        Optional<ServerPlayer.RespawnPosAngle> resolved = ServerPlayer.findRespawnAndUseSpawnBlock(level, respawn, false);
        if (resolved.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_RESPAWN_NO_LOCATION, Component.text(player.getName()));
            return;
        }
        ServerPlayer.RespawnPosAngle destination = resolved.get();
        String name = player.getName();
        Teleport.builder()
                .server(ServerConfig.serverId())
                .position(
                        level.getWorld().getName(),
                        destination.position().x(),
                        destination.position().y(),
                        destination.position().z(),
                        destination.yaw(),
                        destination.pitch()
                )
                .commit(player)
                .onResult(result -> {
                    switch (result) {
                        case TeleportResult.Success ignored -> this.handleFeedback(
                                context,
                                player == context.sender() ? MessageConstants.COMMAND_RESPAWN_SUCCESS_SELF : MessageConstants.COMMAND_RESPAWN_SUCCESS,
                                Component.text(name)
                        );
                        case TeleportResult.Fail fail -> this.handleFeedback(context, MessageConstants.COMMAND_RESPAWN_FAILED, Component.text(name), fail.reason());
                        case TeleportResult.Aborted ignored -> this.handleFeedback(context, MessageConstants.COMMAND_RESPAWN_FAILED, Component.text(name), MessageConstants.TELEPORT_CANCELLED);
                    }
                });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "respawn";
    }
}