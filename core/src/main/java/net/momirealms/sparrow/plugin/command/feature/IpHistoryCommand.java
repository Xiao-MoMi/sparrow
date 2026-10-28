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
import net.momirealms.sparrow.plugin.command.panel.PanelText;
import net.momirealms.sparrow.plugin.command.panel.PanelTone;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
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
                .required("ip", TokenParser.tokenParser())
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
                this.addressPanel(uuid, name, new TextPage<>(0, PAGE_SIZE, 0, List.of()), Optional.empty(), arguments).send(context.sender());
                return CompletableFuture.completedFuture(null);
            }
            int pages = Math.toIntExact(Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE));
            int page = Math.min(requestedPage, pages);
            CompletableFuture<Optional<PlayerIpRecord>> latest = store.findLatestByUuid(uuid);
            return store.listAddresses(uuid, (page - 1) * PAGE_SIZE, PAGE_SIZE).thenAcceptBoth(latest, (records, recent) ->
                    this.addressPanel(uuid, name, new TextPage<>(page - 1, PAGE_SIZE, total, records), recent, arguments).send(context.sender()));
        });
    }

    private CommandPanel addressPanel(UUID uuid, Component name, TextPage<PlayerIpRecord> page, Optional<PlayerIpRecord> latest, String arguments) {
        Component recentAddress = latest.<Component>map(record -> Component.text(record.ip().getHostAddress())).orElse(Component.translatable("command.ip-history.panel.empty"));
        Component details = Component.translatable("command.ip-history.panel.identity", name, Component.text(uuid.toString()), recentAddress);
        Component players = Component.translatable("command.ip-history.panel.players");
        CommandPanel.Builder panel = CommandPanel.list()
                .title(PanelText.of(Component.translatable("command.ip-history.panel.title", name)).copy(uuid.toString()).hover(details))
                .status(PanelText.of(Component.translatable("command.panel.total", Component.text(page.total()))).tone(PanelTone.MUTED))
                .actionColumn("players", players);
        List<PlayerIpRecord> records = page.content();
        for (int i = 0, size = records.size(); i < size; i++) {
            PlayerIpRecord record = records.get(i);
            String address = record.ip().getHostAddress();
            boolean recent = latest.isPresent() && latest.get().ip().equals(record.ip());
            PanelText badge = recent
                    ? PanelText.of("●").tone(PanelTone.SUCCESS).hover(Component.translatable("command.ip-history.panel.latest"))
                    : PanelText.of("");
            panel.addressEntry(
                    PanelText.of(address).copy(address),
                    badge,
                    this.recordedAt(record),
                    PanelButton.run(players, this.getFeatureID(), "ip " + address + " 1").column("players")
            );
        }
        if (records.isEmpty()) {
            panel.entry(PanelText.of(Component.translatable("command.ip-history.panel.empty")).tone(PanelTone.MUTED));
        }
        return panel.pagination(page, this.getFeatureID(), number -> arguments + " " + number).build();
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
            List<Optional<SparrowPlayer>> resolved = players.stream().map(CompletableFuture::join).toList();
            this.playersPanel(ip, page, resolved).send(sender);
        });
    }

    private CommandPanel playersPanel(InetAddress ip, TextPage<PlayerIpRecord> page, List<Optional<SparrowPlayer>> players) {
        String address = ip.getHostAddress();
        Component profile = Component.translatable("command.panel.profile");
        CommandPanel.Builder panel = CommandPanel.list()
                .title(PanelText.of(Component.translatable("command.ip-history.panel.title", Component.text(address))).copy(address))
                .status(PanelText.of(Component.translatable("command.ip-history.panel.player-count", Component.text(page.total()))).tone(PanelTone.MUTED))
                .actionColumn("profile", profile);
        for (int i = 0, size = page.content().size(); i < size; i++) {
            PlayerIpRecord record = page.content().get(i);
            String uuid = record.uuid().toString();
            Optional<SparrowPlayer> player = players.get(i);
            Component name = player.<Component>map(found -> Component.text(found.name()))
                    .orElse(Component.translatable("command.ip-history.panel.unknown-name", Component.text(uuid.substring(0, 8))));
            panel.historyEntry(
                    PanelText.of(name).copy(uuid).hover(Component.translatable("command.ip-history.panel.player-hover", name, Component.text(uuid))),
                    PanelText.of(""),
                    this.recordedAt(record),
                    PanelButton.run(profile, "player_info", player.map(SparrowPlayer::name).orElse(uuid)).column("profile")
            );
        }
        if (page.content().isEmpty()) {
            panel.entry(PanelText.of(MessageConstants.COMMAND_IP_HISTORY_NO_PLAYERS).tone(PanelTone.MUTED));
        }
        return panel.pagination(page, this.getFeatureID(), number -> "ip " + address + " " + number).build();
    }

    private PanelText recordedAt(PlayerIpRecord record) {
        return PanelText.time(record.observedAt()).hover(Component.translatable("command.panel.observed-at", PanelText.fullTime(record.observedAt()).getComponent()));
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "ip_history";
    }
}