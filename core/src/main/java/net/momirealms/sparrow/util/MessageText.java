package net.momirealms.sparrow.util;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.jetbrains.annotations.NotNull;

public final class MessageText {
    private final Format format;
    private final String value;

    public MessageText(@NotNull Component component) {
        this.format = Format.COMPONENT_JSON;
        this.value = AdventureHelper.componentToJson(component);
    }

    public MessageText(@NotNull String miniMessage) {
        this.format = Format.MINI_MESSAGE;
        this.value = miniMessage;
    }

    public MessageText(@NotNull FriendlyByteBuf buffer) {
        this.format = buffer.readEnum(Format.class);
        this.value = ByteBufHelper.readUtf8(buffer, 262144);
    }

    public void write(@NotNull FriendlyByteBuf buffer) {
        buffer.writeEnum(this.format);
        ByteBufHelper.writeUtf8(buffer, this.value, 262144);
    }

    @NotNull
    public Component resolve(@NotNull BukkitSparrowPlayer player) {
        return switch (this.format) {
            case COMPONENT_JSON -> AdventureHelper.jsonToComponent(this.value);
            case MINI_MESSAGE -> AdventureHelper.miniMessage(this.value, player.platformPlayer);
        };
    }

    private enum Format {
        COMPONENT_JSON, MINI_MESSAGE
    }
}