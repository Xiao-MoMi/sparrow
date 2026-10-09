package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.feature.ban.BanFeature;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanTexts;
import net.momirealms.sparrow.feature.mute.MuteFeature;
import net.momirealms.sparrow.feature.mute.MuteRecord;
import net.momirealms.sparrow.feature.mute.MuteTexts;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.cluster.PlayerPresence;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
import net.momirealms.sparrow.util.CharacterUtils;
import net.momirealms.sparrow.util.DateTimeUtils;
import net.momirealms.sparrow.util.DurationUtils;
import net.momirealms.sparrow.util.IpRange;
import net.momirealms.sparrow.util.UUIDUtils;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerInfoCommand extends BukkitCommandFeature {

    public PlayerInfoCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(org.incendo.cloud.@NonNull CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required("player", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerDirectory()))
                .handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String input = context.get("player");
        UUID uuid = UUIDUtils.parse(input);
        CompletableFuture<Optional<PlayerData>> loading = uuid != null
                ? this.plugin().dataStorage().loadPlayer(uuid)
                : this.plugin().playerLookup().resolvePlayer(input)
                        .thenCompose(found -> found.isEmpty()
                                ? CompletableFuture.completedFuture(Optional.empty())
                                : this.plugin().dataStorage().loadPlayer(found.get().uuid()));
        loading.thenCompose(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(sender, MessageConstants.COMMAND_UNKNOWN_PLAYER, Component.text(input));
                return CompletableFuture.completedFuture(null);
            }
            PlayerData data = found.get();
            BanFeature feature = this.plugin().featureManager().feature(BanFeature.ID, BanFeature.class);
            CommandPanel panel = new CommandPanel(this.commandManager(), sender);
            boolean showBans = feature.enabled() && panel.run(Component.empty(), "ban-history", "").available();
            long ip = data.lastLoginIp() == null ? IpRange.NONE : IpRange.parse(data.lastLoginIp()).start();
            CompletableFuture<Optional<BanRecord>> active = showBans
                    ? feature.store().findActiveBan(data.player(), ip, System.currentTimeMillis())
                    : CompletableFuture.completedFuture(Optional.empty());
            MuteFeature muteFeature = this.plugin().featureManager().feature(MuteFeature.ID, MuteFeature.class);
            boolean showMutes = muteFeature.enabled();
            CompletableFuture<Optional<MuteRecord>> activeMute = showMutes
                    ? muteFeature.store().findActive(data.player(), System.currentTimeMillis())
                    : CompletableFuture.completedFuture(Optional.empty());
            return active.thenCombine(activeMute, (ban, mute) -> {
                this.render(sender, data, ban.orElse(null), showBans, mute.orElse(null), showMutes);
                return null;
            });
        })
                .exceptionally(error -> {
                    this.plugin().logger().warn("Failed to query player information of " + input, error);
                    this.handleFeedback(sender, MessageConstants.COMMAND_DATABASE_FAILED);
                    return null;
                });
    }

    private void render(@NotNull CommandSender sender, @NotNull PlayerData data, @Nullable BanRecord ban, boolean showBans, @Nullable MuteRecord mute, boolean showMutes) {
        long now = System.currentTimeMillis();
        boolean compact = sender instanceof Player;
        String uuid = data.player().toString();
        Component identity = Component.text(uuid);
        if (compact) {
            identity = Component.text(uuid.substring(0, 8) + "…" + uuid.substring(32))
                    .hoverEvent(Component.translatable("command.player-info.copy", Component.text(uuid)))
                    .clickEvent(ClickEvent.copyToClipboard(uuid));
        }
        Component unknown = Component.translatable("command.player-info.unknown");
        PlayerPresence online = this.plugin().playerDirectory().find(data.player());
        SparrowPlayer local = this.plugin().playerManager().getPlayer(data.player());
        String server = online == null ? data.lastLogoutServer() : online.server();
        Component serverName = unknown;
        if (server != null) {
            serverName = Component.text(compact ? CharacterUtils.truncate(server, 16) : server);
            if (compact) {
                serverName = serverName.hoverEvent(Component.text(server));
            }
        }
        CommandPanel panel = new CommandPanel(this.commandManager(), sender);
        Component status = Component.translatable(
                "command.player-info.status",
                Component.translatable(online == null ? "command.player-info.offline" : "command.player-info.online"),
                serverName
        );
        if (local != null) {
            status = status.append(Component.translatable("command.player-info.ping", Component.text(local.platformPlayer().getPing())));
        }
        panel.line(Component.translatable("command.player-info.title", Component.text(data.name())))
                .line(status)
                .line(Component.translatable("command.player-info.uuid", identity));
        if (panel.run(Component.empty(), "ip", "").available()) {
            String ip = data.lastLoginIp();
            Component address = unknown;
            if (ip != null) {
                address = Component.text(compact ? CharacterUtils.truncate(ip, 24) : ip);
                if (compact) {
                    address = address.hoverEvent(Component.translatable("command.player-info.copy", Component.text(ip)))
                            .clickEvent(ClickEvent.copyToClipboard(ip));
                }
            }
            panel.line(Component.translatable("command.player-info.ip", address));
        }
        Component session = local == null ? Component.empty() : Component.translatable(
                "command.player-info.session",
                Component.text(DurationUtils.format(now - local.connection().connectedAt()))
        );
        panel.line(Component.translatable("command.player-info.last-login", this.time(data.lastLogin(), compact), session));
        Component logoutLocation = Component.translatable(
                "command.player-info.logout-location",
                data.lastLogoutLocation() == null ? unknown : this.position(data.lastLogoutLocation(), false)
        );
        Component logoutTime = this.time(data.lastLogout(), compact);
        if (compact) {
            Component fullTime = data.lastLogout() == 0 ? unknown : Component.text(DateTimeUtils.fullTime(data.lastLogout()));
            logoutTime = logoutTime.hoverEvent(fullTime.append(Component.newline()).append(logoutLocation));
        }
        panel.line(Component.translatable("command.player-info.last-logout", logoutTime));
        if (local != null) {
            panel.line(Component.translatable(
                    "command.player-info.current-location",
                    this.position(WorldLocation.from(local.platformPlayer().getLocation()), compact)
            ));
        }
        if (!compact || local == null) {
            panel.line(Component.translatable(
                    "command.player-info.logout-location",
                    data.lastLogoutLocation() == null ? unknown : this.position(data.lastLogoutLocation(), compact)
            ));
        }
        if (showBans) {
            Component banStatus = Component.translatable("command.player-info.not-banned");
            if (ban != null) {
                Component full = Component.translatable("command.player-info.banned", BanTexts.id(ban.id()), BanTexts.expiry(ban.expiresAt(), now));
                banStatus = compact
                        ? Component.translatable("command.player-info.banned-short", BanTexts.id(ban.id())).hoverEvent(full)
                        : full;
            }
            panel.line(Component.translatable("command.player-info.ban", banStatus));
        }
        Component muteStatus = Component.translatable(showMutes ? "command.player-info.not-muted" : "command.player-info.mute-disabled");
        if (showMutes && mute != null && mute.active(now)) {
            muteStatus = MuteTexts.describe("command.player-info.muted", mute, now);
        }
        panel.line(Component.translatable("command.player-info.mute", muteStatus));
        PanelButton refresh = panel.run(Component.translatable("command.player-info.refresh"), this.getFeatureID(), uuid)
                .style(PanelButton.Style.INFO)
                .description(Component.translatable("command.player-info.refresh-hover"));
        PanelButton history = panel.suggest(Component.translatable("command.player-info.ip-history"), "ip-history", uuid)
                .style(PanelButton.Style.INFO)
                .description(Component.translatable("command.player-info.ip-history-hover"))
                .disabled(data.lastLoginIp() == null ? MessageConstants.COMMAND_NO_ADDRESS.arguments(Component.text(data.name())) : null);
        BanFeature feature = this.plugin().featureManager().feature(BanFeature.ID, BanFeature.class);
        Component banUnavailable = feature.enabled() ? null : Component.translatable("command.panel.unavailable");
        PanelButton bans = panel.suggest(Component.translatable("command.player-info.ban-history"), "ban-history", uuid)
                .style(PanelButton.Style.INFO)
                .description(Component.translatable("command.player-info.ban-history-hover"))
                .disabled(banUnavailable);
        PanelButton teleport = panel.suggest(Component.translatable("command.player-info.teleport"), "tp-offline", data.name())
                .style(PanelButton.Style.INFO)
                .description(Component.translatable("command.player-info.teleport-hover")
                        .append(Component.newline())
                        .append(logoutLocation))
                .playersOnly()
                .disabled(data.lastLogoutLocation() == null || data.lastLogoutServer() == null
                        ? MessageConstants.COMMAND_TP_OFFLINE_NO_LOCATION.arguments(Component.text(data.name())) : null);
        panel.actions(refresh.build(), history.build(), bans.build(), teleport.build());
        panel.send();
    }

    @NotNull
    private Component time(long millis, boolean compact) {
        if (millis == 0) {
            return Component.translatable("command.player-info.unknown");
        }
        String full = DateTimeUtils.fullTime(millis);
        return compact
                ? Component.text(DateTimeUtils.shortTime(millis)).hoverEvent(Component.text(full))
                : Component.text(full);
    }

    @NotNull
    private Component position(@NotNull WorldLocation location, boolean compact) {
        Component position = Component.translatable(
                "command.player-info.location",
                Component.text(compact ? CharacterUtils.truncate(location.world(), 9) : location.world()),
                Component.text(Math.round(location.x() * 10) / 10.0),
                Component.text(Math.round(location.y() * 10) / 10.0),
                Component.text(Math.round(location.z() * 10) / 10.0)
        );
        if (compact) {
            position = position.hoverEvent(Component.translatable(
                    "command.player-info.location-detail",
                    Component.text(location.world()),
                    Component.text(location.x()),
                    Component.text(location.y()),
                    Component.text(location.z())
            ));
        }
        return position;
    }

    @Override
    @NotNull
    public String getFeatureID() {
        return "player-info";
    }
}