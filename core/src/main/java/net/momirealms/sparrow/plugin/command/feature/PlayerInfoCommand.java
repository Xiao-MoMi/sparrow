package net.momirealms.sparrow.plugin.command.feature;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.database.model.PlayerProfile;
import net.momirealms.sparrow.feature.ban.BanFeature;
import net.momirealms.sparrow.feature.ban.BanRecord;
import net.momirealms.sparrow.feature.ban.BanTexts;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.BukkitCommandFeature;
import net.momirealms.sparrow.plugin.command.CommandConfig;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.panel.CommandPanel;
import net.momirealms.sparrow.plugin.command.panel.PanelButton;
import net.momirealms.sparrow.plugin.command.parser.TokenParser;
import net.momirealms.sparrow.plugin.command.panel.PanelText;
import net.momirealms.sparrow.plugin.command.panel.PanelTone;
import net.momirealms.sparrow.util.DurationUtils;
import net.momirealms.sparrow.util.IpRange;
import net.momirealms.sparrow.util.ServerLocation;
import net.momirealms.sparrow.util.UUIDUtils;
import net.momirealms.sparrow.util.WorldLocation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerInfoCommand extends BukkitCommandFeature {

    public PlayerInfoCommand(@NotNull CommandManager manager, @NotNull SparrowPlugin plugin) {
        super(manager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        manager.command(builder.required(
                        "player",
                        TokenParser.tokenParser(),
                        (context, input) -> CompletableFuture.completedFuture(this.plugin().playerManager().registry.suggestGlobalPlayers(input.peekString()))
                )
                .futureHandler(this::execute));
    }

    private CompletableFuture<Void> execute(CommandContext<CommandSender> context) {
        String input = context.get("player");
        UUID uuid = UUIDUtils.parse(input);
        CompletableFuture<Optional<UUID>> identity = uuid == null
                ? this.plugin().playerManager().getPlayer(input).thenApply(found -> found.map(SparrowPlayer::uniqueId))
                : CompletableFuture.completedFuture(Optional.of(uuid));
        return identity.thenCompose(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_INFO_NOT_FOUND, Component.text(input));
                return CompletableFuture.completedFuture(null);
            }
            return this.load(context, found.get(), input);
        });
    }

    private CompletableFuture<Void> load(CommandContext<CommandSender> context, UUID uuid, String input) {
        return this.plugin().dataStorage().playerProfileStore().load(uuid).thenCompose(found -> {
            if (found.isEmpty()) {
                this.handleFeedback(context, MessageConstants.COMMAND_PLAYER_INFO_NOT_FOUND, Component.text(input));
                return CompletableFuture.completedFuture(null);
            }
            PlayerProfile profile = found.get();
            boolean showIp = this.canUse(context.sender(), "ip_history");
            boolean banEnabled = this.plugin().featureManager().isEnabled(BanFeature.ID);
            boolean showBan = banEnabled && this.canUse(context.sender(), "ban_history");
            long ip = profile.ip() == null ? IpRange.NONE : IpRange.address(profile.ip());
            CompletableFuture<Optional<BanRecord>> ban = showBan
                    ? this.plugin().dataStorage().banStore().findActiveBan(uuid, ip, System.currentTimeMillis())
                    : CompletableFuture.completedFuture(Optional.empty());
            return ban.thenAccept(record -> this.render(context.sender(), uuid, profile, showIp, showBan, record.orElse(null)));
        });
    }

    private void render(CommandSender sender, UUID playerId, PlayerProfile profile, boolean showIp, boolean showBan, @Nullable BanRecord ban) {
        long now = System.currentTimeMillis();
        String uuid = playerId.toString();
        OfflineSparrowPlayer online = this.plugin().playerManager().getGlobalOnlinePlayer(playerId);
        BukkitSparrowPlayer local = online instanceof BukkitSparrowPlayer player ? player : null;
        String name = online == null ? profile.name() : online.name();
        ServerLocation logout = profile.logoutLocation();
        WorldLocation current = local == null ? null : local.getWorldLocation();
        String server = online == null ? (logout == null ? null : logout.server()) : online.getCurrentServerId();
        Component history = this.tr("panel.history");
        Component detail = this.tr("panel.details");
        Component teleport = this.tr("panel.teleport");
        String status = online == null ? "offline" : (local == null ? "remote" : "local");
        CommandPanel.Builder panel = CommandPanel.card()
                .title(PanelText.of(name).hover(this.tr("panel.identity", Component.text(name), Component.text(uuid))).copy(uuid))
                .status(PanelText.of(this.tr("panel." + status)).tone(online == null ? PanelTone.MUTED : PanelTone.SUCCESS))
                .titleActions(
                        PanelButton.run(this.tr("button.name-history"), "name_history", name),
                        PanelButton.run(Component.translatable("command.panel.refresh"), this.getFeatureID(), name)
                )
                .actionColumn("details", detail)
                .actionColumn("primary", history, teleport)
                .field(this.tr(online == null ? "panel.last-place" : "panel.place"), this.place(server, current == null ? (online == null && logout != null ? logout.world() : null) : current.world()));
        if (local != null) {
            panel.field(this.tr("panel.session"), PanelText.of(this.tr("panel.session-value", Component.text(local.platformPlayer.getPing()), Component.text(DurationUtils.format(now - local.connection.connectedAt())))));
        }
        if (showIp) {
            PanelText ip = profile.ip() == null ? PanelText.of(this.tr("unknown")) : PanelText.of(profile.ip().getHostAddress()).copy(profile.ip().getHostAddress());
            panel.field(this.tr("panel.ip"), ip.tone(PanelTone.INFO), PanelButton.run(history, "ip_history", "player " + name).column("primary"));
        }
        if (current != null) {
            panel.field(this.tr("panel.position"), PanelText.coordinates(current.x(), current.y(), current.z()));
        }
        panel.field(this.tr("panel.activity"), PanelText.of(this.tr("panel.activity-value", this.time(profile.loginAt()).getComponent(), this.time(profile.logoutAt()).getComponent())));
        PanelText logoutPoint = logout == null
                ? PanelText.of(this.tr("unknown"))
                : PanelText.coordinates(logout.x(), logout.y(), logout.z()).hover(this.tr("panel.logout-hover", Component.text(logout.server()), Component.text(logout.world()), this.time(profile.logoutAt()).getComponent()));
        panel.field(
                this.tr("panel.logout"),
                logoutPoint,
                PanelButton.suggest(teleport, "tp_offline", name)
                        .column("primary")
                        .disabledIf(!(sender instanceof Player), MessageConstants.COMMAND_PLAYER_REQUIRED)
                        .disabledIf(logout == null, MessageConstants.COMMAND_TP_OFFLINE_NO_LOCATION.arguments(Component.text(name)))
        );
        if (showBan) {
            PanelButton banHistory = PanelButton.run(history, "ban_history", "player " + name).column("primary");
            if (ban == null) {
                panel.field(this.tr("panel.ban"), PanelText.of(this.tr("panel.no-ban")).tone(PanelTone.SUCCESS), banHistory);
            } else {
                Component expiry = ban.permanent() ? MessageConstants.BAN_EXPIRY_PERMANENT : this.time(ban.expiresAt()).getComponent();
                Component tooltip = this.tr("panel.ban-hover", Component.text(ban.id()), BanTexts.reason(ban.reason()), Component.text(ban.operatorName()), expiry);
                panel.field(
                        this.tr("panel.ban"),
                        PanelText.of(this.tr("panel.active-ban", Component.text(ban.id()))).hover(tooltip).tone(PanelTone.DANGER),
                        PanelButton.run(detail, "ban_info", Long.toString(ban.id())).column("details"),
                        banHistory
                );
            }
        }
        panel.build().send(sender);
    }

    private PanelText place(@Nullable String server, @Nullable String world) {
        if (server == null) return PanelText.of(this.tr("unknown"));
        Component value = world == null ? Component.text(server) : this.tr("panel.place-value", Component.text(server), Component.text(world));
        return PanelText.of(value).tone(PanelTone.INFO);
    }

    private Component tr(String key, Component... arguments) {
        return Component.translatable("command.player-info." + key, arguments);
    }

    private boolean canUse(CommandSender sender, String id) {
        CommandFeature command = this.commandManager().feature(id);
        if (command == null) return false;
        CommandConfig config = command.commandConfig();
        String permission = config.getPermission();
        return (permission == null || permission.isEmpty() || sender.hasPermission(permission))
                && config.getUsages().stream().anyMatch(usage -> usage.startsWith("/"));
    }

    private PanelText time(long millis) {
        return millis == 0 ? PanelText.of(this.tr("unknown")) : PanelText.time(millis);
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "player_info";
    }
}