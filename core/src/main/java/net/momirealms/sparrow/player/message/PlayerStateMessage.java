package net.momirealms.sparrow.player.message;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class PlayerStateMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "player_state");
    public static final MessageCodec<FriendlyByteBuf, PlayerStateMessage> CODEC = RedisMessage.codec(PlayerStateMessage::write, PlayerStateMessage::new);

    private final UUID playerId;
    private final Action action;
    private final double value;

    public PlayerStateMessage(@NotNull UUID playerId, @NotNull Action action, double value) {
        this.playerId = playerId;
        this.action = action;
        this.value = value;
    }

    private PlayerStateMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
        this.action = buffer.readEnum(Action.class);
        this.value = buffer.readDouble();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
        buffer.writeEnum(this.action);
        buffer.writeDouble(this.value);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        BukkitSparrowPlayer target = plugin.playerManager().getOnlinePlayer(this.playerId);
        if (target == null) return;
        PlatformExecutor executor = plugin.scheduler().platform();
        Runnable apply = () -> {
            if (target.isOnlineLocally()) {
                this.apply(target.platformPlayer);
            }
        };
        if (executor.isOwnedByCurrentRegion(target.platformPlayer)) {
            apply.run();
        } else {
            executor.run(apply, () -> {}, target.platformPlayer);
        }
    }

    private void apply(Player player) {
        switch (this.action) {
            case HEAL -> {
                if (player.isDead()) return;
                double maximum = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                player.setHealth(Math.min(maximum, player.getHealth() + (this.value < 0 ? maximum : this.value)));
                player.setFoodLevel(20);
                player.setSaturation(10.0f);
            }
            case FEED -> {
                player.setFoodLevel(player.getFoodLevel() + Math.min((int) this.value, 20 - player.getFoodLevel()));
                player.setSaturation(10.0f);
            }
            case AIR -> player.setRemainingAir(this.value < 0 ? player.getMaximumAir() : Math.min((int) this.value, player.getMaximumAir()));
            case BURN -> player.setFireTicks((int) this.value);
            case EXTINGUISH -> player.setFireTicks(0);
            case FLY -> {
                boolean enabled = this.value < 0 ? !player.getAllowFlight() : this.value != 0;
                if (enabled) {
                    player.setAllowFlight(true);
                    player.setFlying(true);
                } else {
                    player.setFlying(false);
                    player.setAllowFlight(false);
                }
            }
            case FLY_SPEED -> player.setFlySpeed((float) this.value);
            case WALK_SPEED -> player.setWalkSpeed((float) this.value);
        }
    }

    public enum Action {
        HEAL, FEED, AIR, BURN, EXTINGUISH, FLY, FLY_SPEED, WALK_SPEED
    }
}