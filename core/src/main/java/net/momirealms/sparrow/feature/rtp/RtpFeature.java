package net.momirealms.sparrow.feature.rtp;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.service.countdown.CountdownCancelledException;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.teleport.position.rtp.RTPPos;
import net.momirealms.sparrow.teleport.position.rtp.RtpArea;
import net.momirealms.sparrow.teleport.processor.post.Invulnerability;
import net.momirealms.sparrow.teleport.processor.post.PlaySound;
import net.momirealms.sparrow.teleport.search.ChunkPolicy;
import net.momirealms.sparrow.teleport.search.PhysicalSafety;
import net.momirealms.sparrow.teleport.search.SafetyRule;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class RtpFeature implements Feature {
    public static final String ID = "rtp";
    private static final String COOLDOWN_ID = "rtp";                          // 所有区域和 rtp_range 共用一份冷却
    private static final SafetyRule SAFETY = PhysicalSafety.builder().build(); // 内置的落点安全规则

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private volatile RtpConfig config;

    @NotNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supportsHotEnable() {
        return true;
    }

    @Override
    public boolean supportsHotDisable() {
        return true;
    }

    @NotNull
    private RtpConfig readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/rtp.yml");
        try {
            return YamlMapperFactory.builder()
                    .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                    .build()
                    .create(RtpSettings.class, RtpSettings::new)
                    .load(path)
                    .value()
                    .toConfig();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + path, exception);
        }
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onEnable() {
        this.config = this.readConfig();
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onReload() {
        this.config = this.readConfig();
        return CompletableFuture.completedFuture(null);
    }

    // 已经开始的传送照常走完
    @NotNull
    @Override
    public CompletableFuture<Void> onDisable() {
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    RtpConfig config() {
        return this.config;
    }

    // 按被传送者的权限选出选项, 冷却与目标服检查通过后提交传送. 被传送者在这里收到全部提示, 返回的失败原因留给发起者, 成功时为 null
    @NotNull
    CompletableFuture<@Nullable Component> teleport(@NotNull BukkitSparrowPlayer traveler, @Nullable String server, @NotNull List<RtpArea> areas) {
        RtpConfig.Options options = this.config.select(traveler.platformPlayer);
        CompletableFuture<Duration> remaining = options.cooldown().isZero()
                ? CompletableFuture.completedFuture(Duration.ZERO)
                : this.plugin.cooldownService().remaining(traveler.uniqueId(), COOLDOWN_ID);
        // 冷却在提交之前检查, 冷却中不会让目标服开始搜索
        return remaining.thenCompose(cooldown -> {
                    if (!cooldown.isZero()) {
                        Component seconds = Component.text((cooldown.toMillis() + 999) / 1000);
                        return CompletableFuture.completedFuture(this.notify(traveler, MessageConstants.TELEPORT_COOLDOWN.arguments(seconds)));
                    }
                    String target = server == null ? ServerConfig.serverId() : server;
                    if (!target.equals(ServerConfig.serverId()) && !this.plugin.serverManager().isOnline(target)) {
                        return CompletableFuture.completedFuture(this.notify(traveler, MessageConstants.TELEPORT_REJECTED_SERVER_OFFLINE));
                    }
                    return this.commit(traveler, options, target, areas);
                })
                .exceptionally(error -> {
                    this.plugin.logger().warn("Random teleport failed for " + traveler.name(), error);
                    return this.notify(traveler, MessageConstants.TELEPORT_FAILED);
                });
    }

    private CompletableFuture<@Nullable Component> commit(BukkitSparrowPlayer traveler, RtpConfig.Options options, String server, List<RtpArea> areas) {
        RTPPos position = RTPPos.builder()
                .areas(areas.toArray(RtpArea[]::new))
                .nearbyColumns(2, 4, 4)
                .rules(SAFETY)
                .chunks(ChunkPolicy.ALLOW_GENERATION)
                .maxAttempts(32)
                .maxChecks(10_000)
                .timeout(Duration.ofSeconds(9))
                .build();
        Teleport teleport = Teleport.builder()
                .server(server)
                .position(position)
                .postProcessor(new PlaySound(options.afterSound()));
        if (!options.invulnerability().isZero()) {
            teleport.postProcessor(new Invulnerability((int) options.invulnerability().toSeconds()));
        }
        // 倒计时与目标服的搜索同时进行
        if (!options.warmup().duration().isZero()) {
            teleport.condition(this.plugin.countdownService().start(traveler, options.warmup(), MessageConstants.TELEPORT_WARMUP));
        }
        return teleport.commit(traveler)
                .result()
                .thenApply(result -> switch (result) {
                    case TeleportResult.Success ignored -> {
                        this.startCooldown(traveler, options.cooldown());
                        yield null;
                    }
                    case TeleportResult.Fail fail -> this.notify(traveler, fail.reason());
                    case TeleportResult.Aborted aborted -> this.aborted(traveler, aborted.cause());
                });
    }

    private void startCooldown(BukkitSparrowPlayer traveler, Duration duration) {
        this.plugin.cooldownService()
                .start(traveler.uniqueId(), COOLDOWN_ID, duration)
                .whenComplete((ignored, error) -> {
                    if (error != null) this.plugin.logger().warn("Failed to start random teleport cooldown for " + traveler.name(), error);
                });
    }

    // 倒计时被新的倒计时替换或玩家已经离开时, 不再提示被传送者
    private Component aborted(BukkitSparrowPlayer traveler, Throwable cause) {
        if (cause instanceof CountdownCancelledException cancelled) {
            return switch (cancelled.reason()) {
                case MOVED -> this.notify(traveler, MessageConstants.TELEPORT_CANCELLED_MOVED);
                case DAMAGED -> this.notify(traveler, MessageConstants.TELEPORT_CANCELLED_DAMAGED);
                case REPLACED, QUIT -> MessageConstants.TELEPORT_CANCELLED;
            };
        }
        return this.notify(traveler, MessageConstants.TELEPORT_CANCELLED);
    }

    private Component notify(BukkitSparrowPlayer traveler, Component reason) {
        traveler.sendTranslatedMessage(reason);
        return reason;
    }
}