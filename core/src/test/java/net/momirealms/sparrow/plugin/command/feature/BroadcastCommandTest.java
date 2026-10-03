package net.momirealms.sparrow.plugin.command.feature;

import io.netty.buffer.Unpooled;
import net.kyori.adventure.text.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BroadcastMessage;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.redis.messagebroker.MessageBroker;
import net.momirealms.sparrow.redis.messagebroker.util.ByteBufHelper;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.meta.CommandMeta;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BroadcastCommandTest {
    private final CommandSender sender = mock(CommandSender.class);
    private final CommandManager feedback = mock(CommandManager.class);
    @SuppressWarnings("unchecked")
    private final MessageBroker<FriendlyByteBuf> broker = mock(MessageBroker.class);
    private final PluginConfig.TextOptions text = mock(PluginConfig.TextOptions.class);
    private final org.incendo.cloud.CommandManager<CommandSender> commands = new org.incendo.cloud.CommandManager<>(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler()) {
        @Override
        public boolean hasPermission(CommandSender sender, String permission) {
            return sender.hasPermission(permission);
        }
    };

    @Test
    void broadcastsOnceToEveryServerAndSchedulesEachPlayer() {
        Server source = new Server("Alice");
        Server destination = new Server("Bob", "Charlie");
        BroadcastMessage message = this.execute(source, "broadcast &aHello %player_name% --legacy-color --parse", true, true);
        verify(this.feedback).handleCommandFeedback(same(this.sender), same(MessageConstants.COMMAND_BROADCAST_SENT), any(Component[].class));
        verifyNoMoreInteractions(this.feedback);
        source.deliver(message, this.broker);
        destination.deliver(message, this.broker);
    }

    @Test
    void emptySourceStillPublishesAndSilentOnlySuppressesFeedback() {
        when(this.text.parseLegacyColor()).thenReturn(true);
        Server source = new Server();
        Server destination = new Server("Bob");
        BroadcastMessage message = this.execute(source, "broadcast &aHello %player_name% --silent", true, false);
        verifyNoInteractions(this.feedback);
        source.deliver(message, this.broker);
        destination.deliver(message, this.broker);
    }

    private BroadcastMessage execute(Server source, String command, boolean legacy, boolean placeholders) {
        when(this.sender.hasPermission("sparrow.command.broadcast")).thenReturn(true);
        when(source.plugin.messageBrokerManager().broker()).thenReturn(this.broker);
        new BroadcastCommand(this.feedback, source.plugin).registerCommand(this.commands,
                Command.<CommandSender>newBuilder("broadcast", CommandMeta.empty()).permission("sparrow.command.broadcast"));
        try (var config = mockStatic(PluginConfig.class)) {
            config.when(PluginConfig::text).thenReturn(this.text);
            this.commands.commandExecutor().executeCommand(this.sender, command).join();
        }
        ArgumentCaptor<BroadcastMessage> capture = ArgumentCaptor.forClass(BroadcastMessage.class);
        verify(this.broker).publishOneWay(capture.capture(), eq(""));
        verifyNoMoreInteractions(this.broker);
        for (SparrowPlayer receiver : source.receivers) verify(receiver, never()).sendMessage(any(Component.class));
        BroadcastMessage message = capture.getValue();
        message.setTargetServer("");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BroadcastMessage.CODEC.encode(buffer, message);
            BroadcastMessage decoded = BroadcastMessage.CODEC.decode(buffer);
            buffer.clear();
            BroadcastMessage.CODEC.encode(buffer, decoded);
            assertEquals("", ByteBufHelper.readUtf8(buffer, 32767));
            assertEquals("&aHello %player_name%", buffer.readUtf());
            assertEquals(legacy, buffer.readBoolean());
            assertEquals(placeholders, buffer.readBoolean());
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static final class Server {
        private final SparrowPlugin plugin = mock(SparrowPlugin.class, RETURNS_DEEP_STUBS);
        private final List<SparrowPlayer> receivers = new ArrayList<>();
        private final List<Runnable> pending = new ArrayList<>();

        private Server(String... names) {
            for (int i = 0; i < names.length; i++) {
                String name = names[i];
                SparrowPlayer receiver = mock(SparrowPlayer.class);
                Player player = mock(Player.class);
                when(receiver.platformPlayer()).thenReturn(player);
                this.receivers.add(receiver);
            }
            when(this.plugin.playerManager().getOnlinePlayers()).thenReturn(this.receivers);
            PlatformExecutor platform = this.plugin.scheduler().platform();
            doAnswer(invocation -> {
                this.pending.add(invocation.getArgument(0));
                return null;
            }).when(platform).run(any(Runnable.class), any(Runnable.class), any(Entity.class));
        }

        private void deliver(BroadcastMessage message, MessageBroker<FriendlyByteBuf> broker) {
            try (var singleton = mockStatic(SparrowPlugin.class)) {
                singleton.when(SparrowPlugin::instance).thenReturn(this.plugin);
                message.handle(broker);
            }
            verify(this.plugin.compatibilityManager(), never()).parsePlaceholders(any(), anyString());
            for (SparrowPlayer receiver : this.receivers) verify(receiver, never()).sendMessage(any(Component.class));
            assertEquals(this.receivers.size(), this.pending.size());
            for (int i = 0, size = this.receivers.size(); i < size; i++) {
                Player player = this.receivers.get(i).platformPlayer();
                verify(this.plugin.scheduler().platform()).run(any(Runnable.class), any(Runnable.class), same(player));
            }
        }
    }
}
