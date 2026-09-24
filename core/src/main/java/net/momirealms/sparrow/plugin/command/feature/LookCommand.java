package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.util.EntityUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultipleEntitySelector;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.bukkit.parser.location.LocationParser;
import org.incendo.cloud.bukkit.parser.selector.MultipleEntitySelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.EnumParser;
import org.incendo.cloud.parser.standard.UUIDParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class LookCommand extends BukkitCommandFeature {
    public LookCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.optional("targets", MultipleEntitySelectorParser.multipleEntitySelectorParser())
                .flag(manager.flagBuilder("location").withComponent(LocationParser.locationParser()))
                .flag(manager.flagBuilder("face").withComponent(EnumParser.enumParser(BlockFace.class)))
                .flag(manager.flagBuilder("player").withComponent(PlayerParser.playerParser()))
                .flag(manager.flagBuilder("entity_uuid").withComponent(UUIDParser.uuidParser()))
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        Location location = context.flags().<Location>getValue("location").orElse(null);
        BlockFace face = context.flags().<BlockFace>getValue("face").orElse(null);
        Player targetPlayer = context.flags().<Player>getValue("player").orElse(null);
        UUID uuid = context.flags().<UUID>getValue("entity_uuid").orElse(null);
        int options = (location != null ? 1 : 0) + (face != null ? 1 : 0) + (targetPlayer != null ? 1 : 0) + (uuid != null ? 1 : 0);
        if (options != 1) {
            this.handleFeedback(context, MessageConstants.COMMAND_LOOK_OPTIONS);
            return;
        }
        MultipleEntitySelector selector = context.getOrDefault("targets", null);
        Collection<Entity> entities;
        if (selector != null) {
            entities = List.copyOf(selector.values());
        } else if (context.sender() instanceof Player player) {
            entities = List.of(player);
        } else {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        if (entities.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        if (targetPlayer != null || uuid != null) {
            Entity target = targetPlayer != null ? targetPlayer : Bukkit.getEntity(uuid);
            if (target == null) {
                this.handleFeedback(context, MessageConstants.COMMAND_LOOK_TARGET_MISSING);
                return;
            }
            this.plugin().scheduler().platform().run(() -> {
                Location destination = target instanceof LivingEntity living ? living.getEyeLocation() : target.getLocation();
                this.schedule(context, entities, destination, null);
            }, () -> this.handleFeedback(context, MessageConstants.COMMAND_LOOK_TARGET_MISSING), target);
        } else {
            this.schedule(context, entities, location, face);
        }
    }

    private void schedule(CommandContext<CommandSender> context, Collection<Entity> entities, @Nullable Location destination, @Nullable BlockFace face) {
        for (Entity entity : entities) {
            this.plugin().scheduler().platform().run(() -> {
                Location rotation = entity.getLocation();
                if (destination != null) {
                    if (!rotation.getWorld().equals(destination.getWorld())) {
                        this.handleFeedback(context, MessageConstants.COMMAND_LOOK_DIFFERENT_WORLD, Component.text(entity.getName()));
                        return;
                    }
                    double eyeHeight = entity instanceof LivingEntity living ? living.getEyeHeight() : 0;
                    Vector direction = destination.toVector().subtract(rotation.toVector().add(new Vector(0, eyeHeight, 0)));
                    if (direction.lengthSquared() != 0) {
                        rotation.setDirection(direction);
                    }
                } else if (face == BlockFace.UP || face == BlockFace.DOWN) {
                    rotation.setPitch(face == BlockFace.UP ? -90 : 90);
                } else {
                    rotation.setYaw(switch (face) {
                        case NORTH -> -180;
                        case NORTH_NORTH_EAST -> -157.5f;
                        case NORTH_EAST -> -135;
                        case EAST_NORTH_EAST -> -112.5f;
                        case EAST -> -90;
                        case EAST_SOUTH_EAST -> -67.5f;
                        case SOUTH_EAST -> -45;
                        case SOUTH_SOUTH_EAST -> -22.5f;
                        case SOUTH -> 0;
                        case SOUTH_SOUTH_WEST -> 22.5f;
                        case SOUTH_WEST -> 45;
                        case WEST_SOUTH_WEST -> 67.5f;
                        case WEST -> 90;
                        case WEST_NORTH_WEST -> 112.5f;
                        case NORTH_WEST -> 135;
                        case NORTH_NORTH_WEST -> 157.5f;
                        default -> rotation.getYaw();
                    });
                }
                String name = entity.getName();
                EntityUtils.rotate(entity, rotation).whenComplete((success, error) -> {
                    if (error != null) {
                        this.plugin().logger().warn("Failed to rotate " + name, error);
                    }
                    this.handleFeedback(context, error == null && success ? MessageConstants.COMMAND_LOOK_SUCCESS : MessageConstants.COMMAND_TELEPORT_FAILURE, Component.text(name));
                });
            }, () -> {}, entity);
        }
    }

    @Override
    public String getFeatureID() {
        return "look";
    }
}
