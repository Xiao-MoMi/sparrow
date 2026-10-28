package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.locale.tag.MessageContext;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class TemplateBroadcastMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "template_broadcast");
    public static final MessageCodec<FriendlyByteBuf, TemplateBroadcastMessage> CODEC = RedisMessage.codec(TemplateBroadcastMessage::write, TemplateBroadcastMessage::new);
    private static final int MAX_TEXT_BYTES = 262144;

    private final String message;
    private final String format;
    private final Map<String, Component> arguments;

    public TemplateBroadcastMessage(@NotNull String message, @NotNull String format, @NotNull Map<String, Component> arguments) {
        this.message = message;
        this.format = format;
        this.arguments = Map.copyOf(arguments);
    }

    private TemplateBroadcastMessage(FriendlyByteBuf buffer) {
        super(buffer);
        this.message = ByteBufHelper.readUtf8(buffer, MAX_TEXT_BYTES);
        this.format = ByteBufHelper.readUtf8(buffer, MAX_TEXT_BYTES);
        int count = buffer.readVarInt();
        Map<String, Component> arguments = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            String name = buffer.readUtf();
            Component component = AdventureHelper.jsonToComponent(ByteBufHelper.readUtf8(buffer, MAX_TEXT_BYTES));
            arguments.put(name, component);
        }
        this.arguments = Map.copyOf(arguments);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.message, MAX_TEXT_BYTES);
        ByteBufHelper.writeUtf8(buffer, this.format, MAX_TEXT_BYTES);
        buffer.writeVarInt(this.arguments.size());
        for (Map.Entry<String, Component> argument : this.arguments.entrySet()) {
            buffer.writeUtf(argument.getKey());
            ByteBufHelper.writeUtf8(buffer, AdventureHelper.componentToJson(argument.getValue()), MAX_TEXT_BYTES);
        }
    }

    @NotNull
    @Override
    public MessageIdentifier identifier() {
        return ID;
    }

    @Override
    protected void handle() {
        this.sendLocally();
    }

    public void sendLocally() {
        for (BukkitSparrowPlayer receiver : SparrowPlugin.instance().playerManager().getOnlinePlayers()) {
            this.sendTo(receiver.platformPlayer);
        }
    }

    public void sendTo(@NotNull Player receiver) {
        new SendMessageMessage(receiver.getUniqueId(), this.render(receiver)).sendLocally();
    }

    @NotNull
    private Component render(@NotNull Player receiver) {
        Map<String, Object> receiverArguments = new HashMap<>(this.arguments);
        Component sentence = AdventureHelper.miniMessage().deserialize(this.message, MessageContext.of(receiver, receiverArguments));
        Map<String, Object> formatArguments = new HashMap<>(receiverArguments);
        formatArguments.put("message", sentence);
        return AdventureHelper.miniMessage().deserialize(this.format, MessageContext.of(receiver, formatArguments));
    }
}