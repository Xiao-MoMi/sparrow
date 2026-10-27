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
import net.momirealms.sparrow.util.DateTimeUtils;
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
            return ban.thenAccept(record -> this.render(context.sender(), uuid, profile, showIp, banEnabled, showBan, record.orElse(null)));
        });
    }

    private void render(CommandSender sender, UUID playerId, PlayerProfile profile, boolean showIp, boolean banEnabled, boolean showBan, @Nullable BanRecord ban) {
        long now = System.currentTimeMillis();
        String uuid = playerId.toString();
        OfflineSparrowPlayer online = this.plugin().playerManager().getGlobalOnlinePlayer(playerId);
        BukkitSparrowPlayer local = online instanceof BukkitSparrowPlayer player ? player : null;
        String name = online == null ? profile.name() : online.name();
        ServerLocation logout = profile.logoutLocation();
        String server = online == null ? (logout == null ? null : logout.server()) : online.getCurrentServerId();
        Component unknown = Component.translatable("command.player-info.unknown");
        CommandPanel.Builder panel = CommandPanel.builder()
                .title(Component.translatable("command.player-info.title", Component.text(name)))
                .copyField(Component.translatable("command.player-info.name"), name)
                .copyField("UUID", uuid)
                .field(Component.translatable("command.player-info.status"), Component.translatable(online == null ? "command.player-info.offline" : "command.player-info.online"))
                .field(
                        Component.translatable(online == null ? "command.player-info.last-server" : "command.player-info.current-server"),
                        server == null ? unknown : Component.text(server)
                );
        if (showIp) {
            if (profile.ip() == null) {
                panel.field(Component.translatable("command.player-info.ip"), unknown);
            } else {
                panel.copyField(Component.translatable("command.player-info.ip"), profile.ip().getHostAddress());
            }
        }
        panel.section(Component.translatable("command.player-info.activity"))
                .field(Component.translatable("command.player-info.last-login"), this.time(profile.loginAt()))
                .field(Component.translatable("command.player-info.last-logout"), this.time(profile.logoutAt()));
        if (local != null) {
            WorldLocation location = local.getWorldLocation();
            panel.section(Component.translatable("command.player-info.local-session"))
                    .field(Component.translatable("command.player-info.ping"), Component.text(local.platformPlayer.getPing() + " ms"))
                    .field(Component.translatable("command.player-info.session"), DurationUtils.format(now - local.connection.connectedAt()))
                    .field(Component.translatable("command.player-info.world"), location.world())
                    .copyField(Component.translatable("command.player-info.coordinates"), location.x() + " " + location.y() + " " + location.z());
        }
        panel.section(Component.translatable("command.player-info.logout-location"));
        if (logout == null) {
            panel.field(Component.translatable("command.player-info.location"), unknown);
        } else {
            panel.field(Component.translatable("command.player-info.server"), logout.server())
                    .field(Component.translatable("command.player-info.world"), logout.world())
                    .copyField(Component.translatable("command.player-info.coordinates"), logout.x() + " " + logout.y() + " " + logout.z());
        }
        if (showBan) {
            Component status = ban == null
                    ? Component.translatable("command.player-info.not-banned")
                    : Component.translatable("command.player-info.banned", BanTexts.id(ban.id()), BanTexts.expiry(ban.expiresAt(), now));
            panel.section(Component.translatable("command.player-info.punishment"))
                    .field(Component.translatable("command.player-info.ban"), status);
        }
        panel.buttons(
                PanelButton.run(Component.translatable("command.panel.refresh"), this.getFeatureID(), uuid),
                PanelButton.run(Component.translatable("command.player-info.button.name-history"), "name_history", name),
                PanelButton.run(Component.translatable("command.player-info.button.ip-history"), "ip_history", "uuid " + uuid),
                PanelButton.run(Component.translatable("command.player-info.button.ban-history"), "ban_history", "player " + uuid)
                        .disabledIf(!banEnabled, Component.translatable("command.player-info.ban-disabled"))
        );
        if (showBan && ban != null) {
            panel.buttons(PanelButton.run(Component.translatable("command.player-info.button.ban-info"), "ban_info", Long.toString(ban.id())));
        }
        panel.buttons(PanelButton.suggest(Component.translatable("command.player-info.button.teleport"), "tp_offline", uuid)
                        .disabledIf(!(sender instanceof Player), MessageConstants.COMMAND_PLAYER_REQUIRED)
                        .disabledIf(logout == null, MessageConstants.COMMAND_TP_OFFLINE_NO_LOCATION.arguments(Component.text(name))))
                .build()
                .send(sender);
    }

    private boolean canUse(CommandSender sender, String id) {
        CommandFeature command = this.commandManager().feature(id);
        if (command == null) return false;
        CommandConfig config = command.commandConfig();
        String permission = config.getPermission();
        return (permission == null || permission.isEmpty() || sender.hasPermission(permission))
                && config.getUsages().stream().anyMatch(usage -> usage.startsWith("/"));
    }

    private Component time(long millis) {
        return millis == 0 ? Component.translatable("command.player-info.unknown") : Component.text(DateTimeUtils.fullTime(millis));
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "player_info";
    }
}