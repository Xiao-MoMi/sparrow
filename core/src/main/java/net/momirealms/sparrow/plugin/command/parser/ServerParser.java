package net.momirealms.sparrow.plugin.command.parser;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ServerParser<C> implements ArgumentParser<C, String>, BlockingSuggestionProvider.Strings<C>, PluginMessageListener {
    public static final String CHANNEL = "BungeeCord"; // Velocity 默认也接受此通道

    private final JavaPlugin plugin;
    private final Predicate<String> filter;

    private volatile List<String> servers = List.of(); // 代理返回的全部后端服务器, 按名称排序
    private volatile @Nullable String currentServer;   // 代理配置中本服的名称, 尚未收到应答时为 null

    /**
     * @param plugin 发送查询所用的插件
     * @param filter 只有通过筛选的服务器会出现在补全中
     */
    public ServerParser(@NotNull JavaPlugin plugin, @NotNull Predicate<String> filter) {
        this.plugin = plugin;
        this.filter = filter;
    }

    @Override
    @NotNull
    public ArgumentParseResult<String> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return ArgumentParseResult.success(input.readString());
    }

    // 借正在输入的玩家向代理刷新, 应答异步到达, 本次返回的是上一次的结果. 控制台没有可用连接, 不补全
    @Override
    @NotNull
    public Iterable<String> stringSuggestions(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        if (!(context.sender() instanceof Player player)) return List.of();
        player.sendPluginMessage(this.plugin, CHANNEL, message("GetServers"));
        player.sendPluginMessage(this.plugin, CHANNEL, message("GetServer"));
        String current = this.currentServer;
        List<String> result = new ArrayList<>();
        for (String server : this.servers) {
            if (!server.equals(current) && this.filter.test(server)) {
                result.add(server);
            }
        }
        return result;
    }

    // 其他插件经同一通道发出的查询也会收到应答, 同样用于更新缓存
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!channel.equals(CHANNEL)) return;
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        switch (in.readUTF()) {
            case "GetServers" -> {
                List<String> servers = new ArrayList<>();
                for (String server : in.readUTF().split(", ")) {
                    if (!server.isEmpty()) servers.add(server);
                }
                servers.sort(String.CASE_INSENSITIVE_ORDER);
                this.servers = List.copyOf(servers);
            }
            case "GetServer" -> this.currentServer = in.readUTF();
            default -> {
            }
        }
    }

    @Nullable
    public String currentServer() {
        return this.currentServer;
    }

    private static byte[] message(String subchannel) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(subchannel);
        return out.toByteArray();
    }
}
