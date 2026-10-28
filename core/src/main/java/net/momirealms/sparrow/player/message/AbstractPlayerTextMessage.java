package net.momirealms.sparrow.player.message;

import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.redis.messagebroker.message.OneWayMessage;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import net.momirealms.sparrow.util.AdventureHelper;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public abstract class AbstractPlayerTextMessage extends OneWayMessage<FriendlyByteBuf> {
    private final UUID playerId;

    protected AbstractPlayerTextMessage(@NotNull UUID playerId) {
        this.playerId = playerId;
    }

    protected AbstractPlayerTextMessage(@NotNull FriendlyByteBuf buffer) {
        super(buffer);
        this.playerId = buffer.readUUID();
    }

    @Override
    protected void write(FriendlyByteBuf buffer) {
        super.write(buffer);
        buffer.writeUUID(this.playerId);
    }

    @Override
    protected final void handle() {
        this.sendLocally();
    }

    public final boolean sendLocally() {
        BukkitSparrowPlayer player = SparrowPlugin.instance().playerManager().getOnlinePlayer(this.playerId);
        if (player == null) return false;
        this.send(player);
        return true;
    }

    protected abstract void send(@NotNull BukkitSparrowPlayer player);

    protected static final class Text {
        private final Format format;
        private final String value;

        Text(@NotNull Component component) {
            this.format = Format.COMPONENT_JSON;
            this.value = AdventureHelper.componentToJson(component);
        }

        Text(@NotNull String miniMessage) {
            this.format = Format.MINI_MESSAGE;
            this.value = miniMessage;
        }

        private Text(@NotNull Format format, @NotNull String value) {
            this.format = format;
            this.value = value;
        }

        Text(@NotNull FriendlyByteBuf buffer) {
            this.format = buffer.readEnum(Format.class);
            this.value = ByteBufHelper.readUtf8(buffer, 262144);
        }

        @NotNull
        static Text translated(@NotNull Component component) {
            return new Text(Format.TRANSLATED_JSON, AdventureHelper.componentToJson(component));
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
}
