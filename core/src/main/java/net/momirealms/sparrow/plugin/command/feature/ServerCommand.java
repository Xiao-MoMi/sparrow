package net.momirealms.sparrow.plugin.command.feature;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.server.ServerFeature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ServerParser;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.ParserDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class ServerCommand extends BukkitCommandFeature {
    private final ServerParser<CommandSender> parser;

    public ServerCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
        this.parser = new ServerParser<>(commandManager, plugin.javaPlugin(), server -> this.plugin().featureManager().feature(ServerFeature.ID, ServerFeature.class).allowed(server));
    }

    // 查询和切服共用 BungeeCord 通道, 随命令注册与注销
    @Override
    public void registerRelatedFunctions() {
        JavaPlugin javaPlugin = this.plugin().javaPlugin();
        Messenger messenger = javaPlugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(javaPlugin, ServerParser.CHANNEL);
        messenger.registerIncomingPluginChannel(javaPlugin, ServerParser.CHANNEL, this.parser);
    }

    @Override
    public void unregisterRelatedFunctions() {
        JavaPlugin javaPlugin = this.plugin().javaPlugin();
        Messenger messenger = javaPlugin.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(javaPlugin, ServerParser.CHANNEL, this.parser);
        messenger.unregisterOutgoingPluginChannel(javaPlugin, ServerParser.CHANNEL);
    }

    @Override
    public Command.Builder<? extends CommandSender> assembleCommand(org.incendo.cloud.CommandManager<CommandSender> manager, Command.Builder<CommandSender> builder) {
        return builder.required("server", ParserDescriptor.of(this.parser, String.class))
                .optional("targets", MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .handler(this::execute);
    }

    private void execute(CommandContext<CommandSender> context) {
        ServerFeature feature = this.plugin().featureManager().feature(ServerFeature.ID, ServerFeature.class);
        if (!feature.enabled()) {
            this.handleFeedback(context, MessageConstants.COMMAND_FEATURE_DISABLED, Component.text(ServerFeature.ID));
            return;
        }
        String server = context.get("server");
        if (!feature.allowed(server)) {
            this.handleFeedback(context, MessageConstants.COMMAND_SERVER_NOT_ALLOWED, Component.text(server));
            return;
        }
        if (server.equals(this.parser.currentServer())) {
            this.handleFeedback(context, MessageConstants.COMMAND_SERVER_CURRENT, Component.text(server));
            return;
        }
        MultiplePlayerSelector selector = context.getOrDefault("targets", null);
        Collection<Player> players;
        if (selector != null) {
            players = selector.values();
        } else if (context.sender() instanceof Player player) {
            players = List.of(player);
        } else {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_REQUIRED);
            return;
        }
        if (players.isEmpty()) {
            this.handleFeedback(context, MessageConstants.COMMAND_TARGETS_EMPTY);
            return;
        }
        for (Player player : players) {
            this.connect(player, server);
            this.handleFeedback(context, MessageConstants.COMMAND_SERVER_SUCCESS, Component.text(player.getName()), Component.text(server));
        }
    }

    // 经由目标玩家自己的连接请求代理切服. 发送后立即返回, 代理找不到服务器或拒绝连接时玩家留在原服
    private void connect(Player player, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(this.plugin().javaPlugin(), ServerParser.CHANNEL, out.toByteArray());
    }

    @Override
    public String getFeatureID() {
        return "server";
    }
}
