package net.momirealms.sparrow.feature.warp;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

public final class WarpFeedbackMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "warp_feedback");
    public static final MessageCodec<FriendlyByteBuf, WarpFeedbackMessage> CODEC = RedisMessage.codec(WarpFeedbackMessage::write, WarpFeedbackMessage::new);

    private final Component message;

    WarpFeedbackMessage(Component message) {
        this.message = message;
    }

    private WarpFeedbackMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.message = AdventureHelper.jsonToComponent(ByteBufHelper.readUtf8(buffer, 262144));
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, AdventureHelper.componentToJson(this.message), 262144);
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        Bukkit.getConsoleSender().sendMessage(SparrowPlugin.instance().translationManager().render(this.message));
    }
}