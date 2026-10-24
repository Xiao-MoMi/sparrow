package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import org.jetbrains.annotations.NotNull;

final class MessageText {
    private final Format format;
    private final String value;

    MessageText(@NotNull Component component) {
        this.format = Format.COMPONENT_JSON;
        this.value = AdventureHelper.componentToJson(component);
    }

    MessageText(@NotNull String miniMessage) {
        this.format = Format.MINI_MESSAGE;
        this.value = miniMessage;
    }

    MessageText(@NotNull Format format, @NotNull String value) {
        this.format = format;
        this.value = value;
    }

    MessageText(@NotNull FriendlyByteBuf buffer) {
        this.format = buffer.readEnum(Format.class);
        this.value = ByteBufHelper.readUtf8(buffer, 262144);
    }

    @NotNull
    static MessageText translated(@NotNull Component component) {
        return new MessageText(Format.TRANSLATED_JSON, AdventureHelper.componentToJson(component));
    }

    void write(@NotNull FriendlyByteBuf buffer) {
        buffer.writeEnum(this.format);
        ByteBufHelper.writeUtf8(buffer, this.value, 262144);
    }

    @NotNull
    Component resolve(@NotNull BukkitSparrowPlayer player) {
        return switch (this.format) {
            case MINI_MESSAGE -> AdventureHelper.miniMessage(this.value, player.platformPlayer);
            case COMPONENT_JSON -> AdventureHelper.jsonToComponent(this.value);
            case TRANSLATED_JSON -> SparrowPlugin.instance().translationManager().render(AdventureHelper.jsonToComponent(this.value), player.locale(), player.platformPlayer);
        };
    }

    private enum Format {
        COMPONENT_JSON, MINI_MESSAGE, TRANSLATED_JSON
    }
}