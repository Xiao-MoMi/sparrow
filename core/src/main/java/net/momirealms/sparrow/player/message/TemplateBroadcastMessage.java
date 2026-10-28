package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.MessageIdentifier;
import net.momirealms.sparrow.redis.messagebroker.RedisMessage;
import net.momirealms.sparrow.redis.messagebroker.codec.MessageCodec;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class TemplateBroadcastMessage extends OneWayMessage<FriendlyByteBuf> {
    public static final MessageIdentifier ID = MessageIdentifier.of("sparrow", "template_broadcast");
    public static final MessageCodec<FriendlyByteBuf, TemplateBroadcastMessage> CODEC = RedisMessage.codec(TemplateBroadcastMessage::write, TemplateBroadcastMessage::new);
    private static final int MAX_TEXT_BYTES = 262144;

    private final MessageTemplate template;

    public TemplateBroadcastMessage(@NotNull MessageTemplate template) {
        this.template = template;
    }

    private TemplateBroadcastMessage(FriendlyByteBuf buffer) {
        super(buffer);
        String message = ByteBufHelper.readUtf8(buffer, MAX_TEXT_BYTES);
        String format = ByteBufHelper.readUtf8(buffer, MAX_TEXT_BYTES);
        int count = buffer.readVarInt();
        Map<String, Component> arguments = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            String name = buffer.readUtf();
            Component component = AdventureHelper.jsonToComponent(ByteBufHelper.readUtf8(buffer, MAX_TEXT_BYTES));
            arguments.put(name, component);
        }
        this.template = new MessageTemplate(message, format, arguments);
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        ByteBufHelper.writeUtf8(buffer, this.template.message(), MAX_TEXT_BYTES);
        ByteBufHelper.writeUtf8(buffer, this.template.format(), MAX_TEXT_BYTES);
        buffer.writeVarInt(this.template.arguments().size());
        for (Map.Entry<String, Component> argument : this.template.arguments().entrySet()) {
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
            new SendMessageMessage(receiver.uniqueId(), this.template.render(receiver.platformPlayer)).sendLocally();
        }
    }
}