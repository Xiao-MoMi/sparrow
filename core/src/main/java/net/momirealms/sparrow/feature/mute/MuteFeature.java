package net.momirealms.sparrow.feature.mute;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.database.MuteStore;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.feature.mute.command.MuteCommand;
import net.momirealms.sparrow.feature.mute.command.UnmuteCommand;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.player.PlayerIdentity;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.util.UUIDUtils;
import net.momirealms.sparrow.util.VersionHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class MuteFeature extends Feature<MuteSettings> implements PlayerListener {
    public static final String ID = "mute";
    public static final String NOTIFY_PERMISSION = DependencyVersions.PROJECT_ID + ".notify.mute";

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Cache<UUID, MuteState> states = Caffeine.newBuilder()
            .expireAfter(new Expiry<UUID, MuteState>() {
                @Override
                public long expireAfterCreate(UUID key, MuteState value, long currentTime) {
                    return retentionNanos(value);
                }

                @Override
                public long expireAfterUpdate(UUID key, MuteState value, long currentTime, long currentDuration) {
                    return retentionNanos(value);
                }

                @Override
                public long expireAfterRead(UUID key, MuteState value, long currentTime, long currentDuration) {
                    return retentionNanos(value);
                }
            })
            .build();
    private Listener chatListener;
    private volatile boolean running;

    public MuteFeature() {
        super(ID);
    }

    @Override
    public void loadConfig() {
        this.config = this.plugin.configurationManager().featuresConfig().config().mute();
    }

    @Override
    protected void onLoad() {
        this.chatListener = VersionHelper.hasPaperPatch ? new PaperMuteListener(this) : new SpigotMuteListener(this);
        Bukkit.getPluginManager().registerEvents(this.chatListener, this.plugin.javaPlugin());
        this.plugin.playerManager().registerListener(this);
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        register.accept(new MuteCommand(this));
        register.accept(new UnmuteCommand(this));
    }

    @Override
    protected void onEnable() {
        this.store().initialize().join();
        this.running = true;
        List<CompletableFuture<Void>> loads = new ArrayList<>();
        for (SparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
            loads.add(this.refresh(player.uniqueId()));
        }
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).join();
    }

    @Override
    protected void onDisable() {
        this.running = false;
        this.states.invalidateAll();
    }

    @Override
    protected void onUnload() {
        this.plugin.playerManager().unregisterListener(this);
        HandlerList.unregisterAll(this.chatListener);
    }

    @Override
    public void onPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        if (!this.running) return;
        this.refresh(event.getUniqueId()).join();
    }

    @Override
    public void onJoin(@NotNull SparrowPlayer player) {
        // 模块可能在该玩家通过预登录之后才启用.
        if (this.running && this.states.getIfPresent(player.uniqueId()) == null) {
            this.refresh(player.uniqueId()).join();
        }
    }

    @NotNull
    public MuteStore store() {
        return this.plugin.dataStorage().muteStore();
    }

    @NotNull
    public CompletableFuture<Optional<PlayerIdentity>> resolvePlayer(@NotNull String input) {
        UUID uuid = UUIDUtils.parse(input);
        return uuid == null ? this.plugin.playerLookup().resolvePlayer(input) : this.plugin.playerLookup().resolvePlayer(uuid);
    }

    @NotNull
    public CompletableFuture<Boolean> mute(@NotNull PlayerIdentity player, @NotNull Duration time, @NotNull String reason, @NotNull String operator) {
        long now = System.currentTimeMillis();
        MuteRecord record = new MuteRecord(
                UUID.randomUUID().toString(), player.uuid(), player.name(), reason, operator, ServerConfig.serverId(), now, Math.addExact(now, time.toMillis()), 0, null
        );
        return this.store().create(record).thenCompose(created -> {
            if (!created) return CompletableFuture.completedFuture(false);
            return this.changed(record).thenApply(ignored -> true);
        });
    }

    @NotNull
    public CompletableFuture<Boolean> unmute(@NotNull UUID player, @NotNull String operator) {
        return this.store().revoke(player, System.currentTimeMillis(), operator).thenCompose(revoked -> {
            if (revoked.isEmpty()) return CompletableFuture.completedFuture(false);
            return this.changed(revoked.get()).thenApply(ignored -> true);
        });
    }

    private CompletableFuture<Void> changed(MuteRecord record) {
        return this.refresh(record.player())
                .thenRun(() -> this.notifyPlayers(record))
                .thenCompose(ignored -> this.plugin.messageBrokerManager().publishOneWay(new MuteMessage(ServerConfig.serverId(), record), ""))
                .thenApply(receivers -> null);
    }

    void accept(@NotNull MuteMessage message) {
        if (!this.running || message.origin().equals(ServerConfig.serverId())) return;
        this.refresh(message.record().player())
                .thenRun(() -> this.notifyPlayers(message.record()))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        this.plugin.logger().warn("Failed to apply mute change for " + message.record().player(), error);
                    }
                });
    }

    private CompletableFuture<Void> refresh(UUID uuid) {
        if (!this.running) return CompletableFuture.completedFuture(null);
        MuteState state = this.states.get(uuid, key -> new MuteState());
        long generation = state.beginLoad();
        return this.store().findActive(uuid, System.currentTimeMillis())
                .thenAccept(record -> {
                    state.complete(generation, record.orElse(null));
                    this.states.getIfPresent(uuid);
                });
    }

    // 有效禁言保留到到期, 无禁言的状态短暂保留后释放, 包括未进入游戏的登录尝试.
    private static long retentionNanos(MuteState state) {
        MuteRecord record = state.record();
        return record == null ? TimeUnit.MINUTES.toNanos(1) : TimeUnit.MILLISECONDS.toNanos(Math.max(0, record.expiresAt() - System.currentTimeMillis()));
    }

    private void notifyPlayers(MuteRecord record) {
        if (!this.running) return;
        long now = System.currentTimeMillis();
        boolean muted = record.revokedAt() == 0;
        SparrowPlayer target = this.plugin.playerManager().getPlayer(record.player());
        if (target != null) {
            target.sendMessage(MuteTexts.describe(muted ? "mute.applied" : "mute.removed", record, now));
        }
        TranslatableComponent notification = MuteTexts.describe(muted ? "mute.notify.applied" : "mute.notify.removed", record, now);
        for (SparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
            if (player.hasPermission(NOTIFY_PERMISSION)) {
                player.sendMessage(notification);
            }
        }
    }

    @Nullable
    TranslatableComponent denial(@NotNull Player player) {
        if (!this.enabled()) return null;
        MuteState state = this.states.getIfPresent(player.getUniqueId());
        if (state == null) return null;
        MuteRecord record = state.record();
        long now = System.currentTimeMillis();
        return record != null && record.active(now) ? MuteTexts.describe("mute.blocked", record, now) : null;
    }

    void reply(@NotNull Player player, @NotNull TranslatableComponent message) {
        this.plugin.commandManager().handleCommandFeedback(player, message.key(), message);
    }
}