package net.momirealms.sparrow.plugin.command.parser;

import com.google.common.io.ByteArrayDataOutput;
import net.momirealms.sparrow.plugin.command.CommandManager;
import com.google.common.io.ByteStreams;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServerParserTest {
    private final CommandManager commands = mock(CommandManager.class);
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private ServerParser<CommandSender> parser;

    @BeforeEach
    void setUp() {
        when(this.commands.asynchronousCompletion()).thenReturn(true);
        this.parser = new ServerParser<>(this.commands, this.plugin, server -> !server.equals("hidden"));
    }

    @Test
    void firstCompletionWaitsForBothProxyResponses() {
        Player player = this.player();
        CompletableFuture<List<String>> suggestions = this.suggest(player);
        assertFalse(suggestions.isDone());

        this.receive(player, "GetServers", "survival, lobby, Creative, hidden, minigame");
        assertFalse(suggestions.isDone());
        this.receive(player, "GetServer", "lobby");

        assertEquals("lobby", this.parser.currentServer());
        assertEquals(List.of("Creative", "minigame", "survival"), suggestions.join());
    }

    @Test
    void simultaneousCompletionsUseTheSameProxyResponse() throws Exception {
        Player player = this.player();
        CompletableFuture<List<String>> first = this.suggest(player);
        CompletableFuture<List<String>> second = this.suggest(player);
        assertEquals(List.of(), this.suggest(mock(CommandSender.class)).join());

        ArgumentCaptor<byte[]> messages = ArgumentCaptor.forClass(byte[].class);
        verify(player, times(4)).sendPluginMessage(same(this.plugin), eq("BungeeCord"), messages.capture());
        List<String> subchannels = new ArrayList<>();
        for (byte[] message : messages.getAllValues()) {
            subchannels.add(new DataInputStream(new ByteArrayInputStream(message)).readUTF());
        }
        assertEquals(List.of("GetServers", "GetServer", "GetServers", "GetServer"), subchannels);

        this.receive(player, "GetServer", "lobby");
        this.receive(player, "GetServers", "lobby, survival");
        assertEquals(List.of("survival"), first.join());
        assertEquals(first.join(), second.join());

        CompletableFuture<List<String>> next = this.suggest(player);
        assertFalse(next.isDone());
        this.receive(player, "GetServers", "lobby, creative");
        assertEquals(List.of("creative"), next.join());
    }

    @Test
    void synchronousCompletionUsesPreviousProxyResponse() {
        when(this.commands.asynchronousCompletion()).thenReturn(false);
        this.parser = new ServerParser<>(this.commands, this.plugin, server -> true);
        Player player = this.player();
        assertEquals(List.of(), this.suggest(player).join());
        this.receive(player, "GetServers", "lobby, survival");
        this.receive(player, "GetServer", "lobby");

        assertEquals(List.of("survival"), this.suggest(player).join());
        verify(player, times(4)).sendPluginMessage(same(this.plugin), eq("BungeeCord"), any(byte[].class));
    }

    private Player player() {
        return mock(Player.class);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<List<String>> suggest(CommandSender sender) {
        CommandContext<CommandSender> context = mock(CommandContext.class);
        when(context.sender()).thenReturn(sender);
        return this.parser.suggestionsFuture(context, CommandInput.empty()).thenApply(suggestions -> {
            List<String> result = new ArrayList<>();
            suggestions.forEach(suggestion -> result.add(suggestion.suggestion()));
            return result;
        });
    }

    private void receive(Player player, String subchannel, String value) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(subchannel);
        out.writeUTF(value);
        this.parser.onPluginMessageReceived("BungeeCord", player, out.toByteArray());
    }
}
