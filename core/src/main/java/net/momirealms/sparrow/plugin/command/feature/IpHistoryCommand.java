package net.momirealms.sparrow.plugin.command.feature;

import io.netty.util.NetUtil;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.PlayerIpHistoryStore;
import net.momirealms.sparrow.database.model.PlayerIpRecord;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.TextPage;
import net.momirealms.sparrow.util.DateTimeUtils;
import net.momirealms.sparrow.util.UUIDUtils;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class IpHistoryCommand extends BukkitCommandFeature {
    private static final int PAGE_SIZE = 8;

    public IpHistoryCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.literal("player")
                .required(
                        "name",
                        StringParser.stringParser(),
                        (context, input) -> CompletableFuture.completedFuture(this.plugin().playerManager().registry.suggestGlobalPlayers(input.peekString()))
                )
                .optional("page", IntegerParser.integerParser(1))
                .futureHandler(this::byPlayer));
        manager.command(builder.literal("uuid")
                .required("uuid", StringParser.stringParser())
                .optional("page", IntegerParser.integerParser(1))
                .futureHandler(this::byUuid));
        manager.command(builder.literal("ip")
                .required("ip", StringParser.stringParser())
                .optional("page", IntegerParser.integerParser(1))
                .futureHandler(this::byIp));
    }

    @NotNull
    private CompletableFuture<Void> byPlayer(@NotNull CommandContext<CommandSender> context) {
        String name = context.get("name");
        return this.plugin().playerManager().getPlayer(name).thenCompose(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_IP_HISTORY_UNKNOWN_PLAYER, Component.text(name));
                return CompletableFuture.completedFuture(null);
            }
            SparrowPlayer player = found.get();
            return this.showAddresses(context, player.uniqueId(), Component.text(player.name()), "player " + name);
        });
    }

    @NotNull
    private CompletableFuture<Void> byUuid(@NotNull CommandContext<CommandSender> context) {
        String input = context.get("uuid");
        UUID uuid = UUIDUtils.parse(input);
        if (uuid == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_NAME_INVALID_UUID, Component.text(input));
            return CompletableFuture.completedFuture(null);
        }
        return this.plugin().playerManager().getPlayer(uuid).thenCompose(found -> this.showAddresses(
                context,
                uuid,
                found.<Component>map(player -> Component.text(player.name())).orElse(MessageConstants.COMMAND_IP_HISTORY_UNKNOWN_NAME),
                "uuid " + uuid
        ));
    }

    @NotNull
    private CompletableFuture<Void> showAddresses(@NotNull CommandContext<CommandSender> context, @NotNull UUID uuid, @NotNull Component name, @NotNull String arguments) {
        PlayerIpHistoryStore store = this.plugin().dataStorage().playerIpHistoryStore();
        int requestedPage = context.getOrDefault("page", 1);
        return store.countAddresses(uuid).thenCompose(total -> {
            if (total == 0) {
                this.handleFeedback(context, MessageConstants.COMMAND_IP_HISTORY_NO_ADDRESSES, Component.text(uuid.toString()));
                return CompletableFuture.completedFuture(null);
            }
            int page = (int) Math.min(requestedPage, (total + PAGE_SIZE - 1) / PAGE_SIZE);
            CompletableFuture<Optional<PlayerIpRecord>> latest = store.findLatestByUuid(uuid);
            return store.listAddresses(uuid, (page - 1) * PAGE_SIZE, PAGE_SIZE).thenAcceptBoth(latest, (records, recent) -> {
                CommandPanel.Builder panel = CommandPanel.builder()
                        .title(MessageConstants.COMMAND_IP_HISTORY_PLAYER_TITLE)
                        .field(MessageConstants.COMMAND_IP_HISTORY_PLAYER, name)
                        .copyField("UUID", uuid.toString())
                        .copyField(MessageConstants.COMMAND_IP_HISTORY_RECENT, recent.orElseThrow().ip().getHostAddress())
                        .field(MessageConstants.COMMAND_IP_HISTORY_ADDRESS_COUNT, Component.text(total))
                        .section(MessageConstants.COMMAND_IP_HISTORY_ADDRESS_RECORDS);
                for (int i = 0, size = records.size(); i < size; i++) {
                    PlayerIpRecord record = records.get(i);
                    panel.copyField(DateTimeUtils.fullTime(record.observedAt()), record.ip().getHostAddress());
                }
                panel.pagination(page, Math.toIntExact((total + PAGE_SIZE - 1) / PAGE_SIZE), this.getFeatureID(), index -> arguments + " " + index)
                        .build()
                        .send(context.sender());
            });
        });
    }

    @NotNull
    private CompletableFuture<Void> byIp(@NotNull CommandContext<CommandSender> context) {
        String input = context.get("ip");
        InetAddress ip = input.indexOf(':') >= 0 ? NetUtil.getByName(input) : NetUtil.createInetAddressFromIpAddressString(input);
        if (ip == null) {
            this.handleFeedback(context, MessageConstants.COMMAND_IP_HISTORY_INVALID_IP, Component.text(input));
            return CompletableFuture.completedFuture(null);
        }
        PlayerIpHistoryStore store = this.plugin().dataStorage().playerIpHistoryStore();
        int requestedPage = context.getOrDefault("page", 1);
        return store.countPlayers(ip).thenCompose(total -> {
            int page = (int) Math.min(requestedPage, Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE));
            return store.listPlayers(ip, (page - 1) * PAGE_SIZE, PAGE_SIZE)
                    .thenCompose(records -> this.showPlayers(context.sender(), ip, new TextPage<>(page - 1, PAGE_SIZE, total, records)));
        });
    }

    @NotNull
    private CompletableFuture<Void> showPlayers(@NotNull CommandSender sender, @NotNull InetAddress ip, @NotNull TextPage<PlayerIpRecord> page) {
        List<PlayerIpRecord> records = page.content();
        List<CompletableFuture<Optional<SparrowPlayer>>> players = new ArrayList<>(records.size());
        for (int i = 0, size = records.size(); i < size; i++) {
            players.add(this.plugin().playerManager().getPlayer(records.get(i).uuid()));
        }
        return CompletableFuture.allOf(players.toArray(CompletableFuture[]::new)).thenRun(() -> {
            String address = ip.getHostAddress();
            CommandPanel.Builder panel = CommandPanel.builder()
                    .title(MessageConstants.COMMAND_IP_HISTORY_IP_TITLE)
                    .copyField("IP", address)
                    .field(MessageConstants.COMMAND_IP_HISTORY_PLAYER_COUNT, Component.text(page.total()));
            if (records.isEmpty()) {
                panel.field(MessageConstants.COMMAND_IP_HISTORY_PLAYERS, MessageConstants.COMMAND_IP_HISTORY_NO_PLAYERS);
            } else {
                panel.section(MessageConstants.COMMAND_IP_HISTORY_PLAYERS);
                for (int i = 0, size = records.size(); i < size; i++) {
                    PlayerIpRecord record = records.get(i);
                    Component name = players.get(i).join()
                            .<Component>map(player -> Component.text(player.name()))
                            .orElse(MessageConstants.COMMAND_IP_HISTORY_UNKNOWN_NAME);
                    Component label = MessageConstants.COMMAND_IP_HISTORY_PLAYER_ROW.arguments(name, Component.text(DateTimeUtils.fullTime(record.observedAt())));
                    panel.copyField(label, record.uuid().toString());
                }
            }
            panel.pagination(page, this.getFeatureID(), index -> "ip " + address + " " + index)
                    .build()
                    .send(sender);
        });
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "ip_history";
    }
}