package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.momirealms.sparrow.database.PlayerData;
import net.momirealms.sparrow.feature.ban.BanFeature;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanTexts;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.player.cluster.ClusterPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.parser.ClusterPlayerParser;
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
    public void registerCommand(
            org.incendo.cloud.@NonNull CommandManager<CommandSender> manager,
            @NotNull Command.Builder<CommandSender> builder
    ) {
        manager.command(builder.required("player", ClusterPlayerParser.clusterPlayerParser(this.plugin().playerManager().cluster()))
                .handler(this::execute));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        CommandSender sender = context.sender();
        String input = context.get("player");
        UUID uuid = UUIDUtils.parse(input);
        CompletableFuture<Optional<PlayerData>> loading = uuid != null
                ? this.plugin().dataStorage().loadPlayer(uuid)
                : this.plugin().playerManager().resolvePlayer(input)
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
            return active.thenAccept(ban -> this.render(sender, data, ban.orElse(null), showBans));
        })
                .exceptionally(error -> {
                    this.plugin().logger().warn("Failed to query player information of " + input, error);
                    this.handleFeedback(sender, MessageConstants.COMMAND_DATABASE_FAILED);
                    return null;
                });
    }

    private void render(@NotNull CommandSender sender, @NotNull PlayerData data, @Nullable BanRecord ban, boolean showBans) {
        long now = System.currentTimeMillis();
        String uuid = data.player().toString();
        Component unknown = Component.translatable("command.player-info.unknown");
        ClusterPlayer online = this.plugin().playerManager().cluster().find(data.player());
        SparrowPlayer local = this.plugin().playerManager().getPlayer(data.player());
        String server = online == null ? data.lastLogoutServer() : online.server();
        CommandPanel panel = new CommandPanel(this.commandManager(), sender);
        panel.line(Component.translatable("command.player-info.title", Component.text(data.name())))
                .line(Component.translatable(
                        "command.player-info.uuid",
                        Component.text(uuid).clickEvent(ClickEvent.copyToClipboard(uuid))
                ))
                .line(Component.translatable(
                        "command.player-info.status",
                        Component.translatable(online == null ? "command.player-info.offline" : "command.player-info.online"),
                        server == null ? unknown : Component.text(server)
                ))
                .line(Component.translatable(
                        "command.player-info.last-login",
                        data.lastLogin() == 0 ? unknown : Component.text(DateTimeUtils.fullTime(data.lastLogin()))
                ))
                .line(Component.translatable(
                        "command.player-info.last-logout",
                        data.lastLogout() == 0 ? unknown : Component.text(DateTimeUtils.fullTime(data.lastLogout()))
                ));
        if (local != null) {
            panel.line(Component.translatable(
                    "command.player-info.session",
                    Component.text(DurationUtils.format(now - local.connection().connectedAt()))
            ));
            panel.line(Component.translatable(
                    "command.player-info.current-location",
                    this.position(WorldLocation.from(local.platformPlayer().getLocation()))
            ));
            panel.line(Component.translatable("command.player-info.ping", Component.text(local.platformPlayer().getPing())));
        }
        panel.line(Component.translatable(
                "command.player-info.logout-location",
                data.lastLogoutLocation() == null ? unknown : this.position(data.lastLogoutLocation())
        ));
        if (panel.run(Component.empty(), "ip", "").available()) {
            Component address = data.lastLoginIp() == null
                    ? unknown
                    : Component.text(data.lastLoginIp()).clickEvent(ClickEvent.copyToClipboard(data.lastLoginIp()));
            panel.line(Component.translatable("command.player-info.ip", address));
        }
        if (showBans) {
            Component status = ban == null
                    ? Component.translatable("command.player-info.not-banned")
                    : Component.translatable("command.player-info.banned", BanTexts.id(ban.id()), BanTexts.expiry(ban.expiresAt(), now));
            panel.line(Component.translatable("command.player-info.ban", status));
        }
        PanelButton history = panel.run(Component.translatable("command.player-info.ip-history"), "ip-history", uuid)
                .disabled(data.lastLoginIp() == null ? MessageConstants.COMMAND_NO_ADDRESS.arguments(Component.text(data.name())) : null);
        BanFeature feature = this.plugin().featureManager().feature(BanFeature.ID, BanFeature.class);
        Component banUnavailable = feature.enabled() ? null : Component.translatable("command.panel.unavailable");
        PanelButton bans = panel.run(Component.translatable("command.player-info.ban-history"), "ban-history", uuid)
                .disabled(banUnavailable);
        PanelButton teleport = panel.run(Component.translatable("command.player-info.teleport"), "tp-offline", data.name())
                .playersOnly()
                .disabled(data.lastLogoutLocation() == null || data.lastLogoutServer() == null
                        ? MessageConstants.COMMAND_TP_OFFLINE_NO_LOCATION.arguments(Component.text(data.name())) : null);
        panel.actions(
                panel.run(CommandPanel.label("refresh"), this.getFeatureID(), uuid).build(),
                history.build(),
                bans.build(),
                teleport.build()
        );
        if (sender instanceof Player) {
            panel.actions(panel.run(Component.translatable("command.player-info.ban-gui"), "ban-history", uuid + " --gui")
                    .disabled(banUnavailable)
                    .build());
        }
        panel.send();
    }

    @NotNull
    private Component position(@NotNull WorldLocation location) {
        return Component.translatable(
                "command.player-info.location",
                Component.text(location.world()),
                Component.text(Math.round(location.x() * 10) / 10.0),
                Component.text(Math.round(location.y() * 10) / 10.0),
                Component.text(Math.round(location.z() * 10) / 10.0)
        );
    }

    @Override
    @NotNull
    public String getFeatureID() {
        return "player-info";
    }
}