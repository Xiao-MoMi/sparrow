package net.momirealms.sparrow.feature.maintenance;

import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import org.jetbrains.annotations.NotNull;

public final class MaintenanceMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "maintenance");
    public static final MessageCodec<FriendlyByteBuf, MaintenanceMessage> CODEC = RedisMessage.codec(MaintenanceMessage::write, MaintenanceMessage::new);

    private final boolean active;

    public MaintenanceMessage(boolean active) {
        this.active = active;
    }

    private MaintenanceMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.active = buffer.readBoolean();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeBoolean(this.active);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        SparrowPlugin plugin = SparrowPlugin.instance();
        plugin.scheduler().platform().execute(() -> {
            if (!plugin.featureManager().isEnabled(MaintenanceFeature.ID)) return;
            plugin.featureManager().feature(MaintenanceFeature.ID, MaintenanceFeature.class).setActive(this.active);
        });
    }
}