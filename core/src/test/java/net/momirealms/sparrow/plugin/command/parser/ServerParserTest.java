package net.momirealms.sparrow.plugin.command.parser;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.incendo.cloud.context.CommandContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerParserTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final ServerParser<CommandSender> parser = new ServerParser<>(this.plugin, server -> !server.equals("hidden"));

    @Test
    void suggestsProxyServersExceptCurrentAndFiltered() {
        Player player = mock(Player.class);
        // 尚未收到代理应答时没有补全, 也不知道本服名称
        assertEquals(List.of(), this.suggest(player));
        assertNull(this.parser.currentServer());
        this.receive("GetServers", "survival, lobby, Creative, hidden, minigame");
        this.receive("GetServer", "lobby");
        assertEquals("lobby", this.parser.currentServer());
        assertEquals(List.of("Creative", "minigame", "survival"), this.suggest(player));
    }

    @Test
    void everyPlayerCompletionQueriesProxyAndConsoleDoesNotComplete() throws Exception {
        Player player = mock(Player.class);
        this.suggest(player);
        this.suggest(player);
        // 每次补全都查询, 没有冷却
        ArgumentCaptor<byte[]> messages = ArgumentCaptor.forClass(byte[].class);
        verify(player, times(4)).sendPluginMessage(same(this.plugin), eq("BungeeCord"), messages.capture());
        List<String> subchannels = new ArrayList<>();
        for (byte[] message : messages.getAllValues()) {
            subchannels.add(new DataInputStream(new ByteArrayInputStream(message)).readUTF());
        }
        assertEquals(List.of("GetServers", "GetServer", "GetServers", "GetServer"), subchannels);
        // 控制台没有连接可借, 即使已有缓存也不补全
        this.receive("GetServers", "survival");
        assertEquals(List.of(), this.suggest(mock(CommandSender.class)));
    }

    @SuppressWarnings("unchecked")
    private List<String> suggest(CommandSender sender) {
        CommandContext<CommandSender> context = mock(CommandContext.class);
        when(context.sender()).thenReturn(sender);
        List<String> result = new ArrayList<>();
        this.parser.stringSuggestions(context, null).forEach(result::add);
        return result;
    }

    private void receive(String subchannel, String value) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(subchannel);
        out.writeUTF(value);
        this.parser.onPluginMessageReceived("BungeeCord", mock(Player.class), out.toByteArray());
    }
}
