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
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class ServerParser<C> implements ArgumentParser<C, String>, SuggestionProvider<C>, PluginMessageListener {
    public static final String CHANNEL = "BungeeCord"; // Velocity 默认也接受此通道

    private final JavaPlugin plugin;
    private final Predicate<String> filter;
    private final boolean asynchronousCompletion;
    private final AtomicReference<CompletableFuture<List<String>>> nextServers = new AtomicReference<>(new CompletableFuture<>());
    private final CompletableFuture<String> currentServerReceived = new CompletableFuture<>();

    private volatile List<String> servers = List.of(); // 代理返回的全部后端服务器, 按名称排序
    private volatile @Nullable String currentServer;   // 代理配置中本服的名称, 尚未收到应答时为 null

    /**
     * @param plugin 发送查询所用的插件
     * @param filter 只有通过筛选的服务器会出现在补全中
     * @param asynchronousCompletion 命令平台是否允许异步等待补全
     */
    public ServerParser(@NotNull JavaPlugin plugin, @NotNull Predicate<String> filter, boolean asynchronousCompletion) {
        this.plugin = plugin;
        this.filter = filter;
        this.asynchronousCompletion = asynchronousCompletion;
    }

    @Override
    @NotNull
    public ArgumentParseResult<String> parse(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        return ArgumentParseResult.success(input.readString());
    }

    // 异步补全等待代理应答; 同步补全本次查询后返回上一次的名单.
    @Override
    @NotNull
    public CompletableFuture<? extends Iterable<? extends Suggestion>> suggestionsFuture(@NotNull CommandContext<C> context, @NotNull CommandInput input) {
        if (!(context.sender() instanceof Player player)) return CompletableFuture.completedFuture(List.of());
        if (!this.asynchronousCompletion) {
            this.query(player);
            return CompletableFuture.completedFuture(this.suggestions(this.servers, this.currentServer));
        }
        CompletableFuture<List<String>> servers = this.nextServers.get();
        this.query(player);
        return servers.thenCombine(this.currentServerReceived, this::suggestions);
    }

    private void query(Player player) {
        player.sendPluginMessage(this.plugin, CHANNEL, message("GetServers"));
        player.sendPluginMessage(this.plugin, CHANNEL, message("GetServer"));
    }

    private List<Suggestion> suggestions(List<String> servers, @Nullable String current) {
        List<Suggestion> result = new ArrayList<>();
        for (String server : servers) {
            if (!server.equals(current) && this.filter.test(server)) {
                result.add(Suggestion.suggestion(server));
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
                this.nextServers.getAndSet(new CompletableFuture<>()).complete(this.servers);
            }
            case "GetServer" -> {
                this.currentServer = in.readUTF();
                this.currentServerReceived.complete(this.currentServer);
            }
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
