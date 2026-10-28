package net.momirealms.sparrow.feature.patrol;

import io.lettuce.core.ScriptOutputType;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.player.OfflineSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.FeaturePermission;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.util.UUIDUtils;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.bukkit.GameMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

public final class PatrolFeature implements Feature {
    public static final String ID = "patrol";
    public static final FeaturePermission COMMAND_PERMISSION = new FeaturePermission(ID);
    private static final String BYPASS_PERMISSION = DependencyVersions.PROJECT_ID + ".bypass.patrol";
    private static final byte[] HISTORY_KEY = "sparrow:patrol:history".getBytes(StandardCharsets.UTF_8);
    // 分数表示全服巡查次序, 选中与移到队尾在同一次 Redis 操作中完成.
    private static final String CLAIM_SCRIPT = """
            local selected = ARGV[1]
            local oldest = tonumber(redis.call('ZSCORE', KEYS[1], selected)) or -1
            for i = 2, #ARGV do
                local score = tonumber(redis.call('ZSCORE', KEYS[1], ARGV[i])) or -1
                if score < oldest then
                    selected = ARGV[i]
                    oldest = score
                end
            end
            local last = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES')
            local sequence = (#last == 0 and 0 or tonumber(last[2])) + 1
            redis.call('ZADD', KEYS[1], sequence, selected)
            return selected
            """;

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private volatile PatrolSettings config;
    private volatile long generation;

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
    private PatrolSettings readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/patrol.yml");
        try {
            return YamlMapperFactory.builder()
                    .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                    .build()
                    .create(PatrolSettings.class, PatrolSettings::new)
                    .load(path)
                    .value();
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
        this.generation++;
        return CompletableFuture.completedFuture(null);
    }

    long generation() {
        return this.generation;
    }

    @NotNull
    CompletableFuture<List<UUID>> filterCandidates(@NotNull List<UUID> candidates) {
        PatrolSettings settings = this.config;
        if (!this.plugin.featureManager().isEnabled(ID)) return CompletableFuture.completedFuture(List.of());
        PlatformExecutor executor = this.plugin.scheduler().platform();
        List<CompletableFuture<UUID>> checks = new ArrayList<>(candidates.size());
        for (int i = 0, size = candidates.size(); i < size; i++) {
            BukkitSparrowPlayer player = this.plugin.playerManager().getOnlinePlayer(candidates.get(i));
            if (player == null) continue;
            CompletableFuture<UUID> check = new CompletableFuture<>();
            check.completeAsync(() -> {
                if (!this.plugin.featureManager().isEnabled(ID) || !player.isOnlineLocally()) return null;
                if (player.hasPermission(BYPASS_PERMISSION)) return null;
                if (settings.skipSpectators() && player.platformPlayer.getGameMode() == GameMode.SPECTATOR) return null;
                return player.uniqueId();
            }, task -> {
                if (executor.isOwnedByCurrentRegion(player.platformPlayer)) {
                    task.run();
                } else {
                    executor.run(task, () -> check.complete(null), player.platformPlayer);
                }
            });
            checks.add(check);
        }
        return CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            List<UUID> eligible = new ArrayList<>();
            for (int i = 0, size = checks.size(); i < size; i++) {
                UUID id = checks.get(i).join();
                if (id != null) eligible.add(id);
            }
            return eligible;
        });
    }

    @NotNull
    CompletableFuture<@Nullable Target> claimNext(@NotNull UUID patroller, @NotNull List<OfflineSparrowPlayer> candidates) {
        long generation = this.generation;
        Map<UUID, Target> targets = new HashMap<>();
        Map<String, List<UUID>> byServer = new LinkedHashMap<>();
        for (int i = 0, size = candidates.size(); i < size; i++) {
            OfflineSparrowPlayer candidate = candidates.get(i);
            if (candidate.uniqueId().equals(patroller)) continue;
            String server = candidate.getCurrentServerId();
            if (server == null) continue;
            targets.put(candidate.uniqueId(), new Target(candidate.uniqueId(), candidate.name(), server));
            byServer.computeIfAbsent(server, ignored -> new ArrayList<>()).add(candidate.uniqueId());
        }
        List<CompletableFuture<PatrolCandidatesResponse>> queries = new ArrayList<>(byServer.size());
        for (Map.Entry<String, List<UUID>> entry : byServer.entrySet()) {
            PatrolCandidatesRequest request = new PatrolCandidatesRequest(entry.getValue());
            queries.add(entry.getKey().equals(ServerConfig.serverId())
                    ? request.execute()
                    : this.plugin.messageBrokerManager().broker().publishTwoWay(request, entry.getKey()));
        }
        return CompletableFuture.allOf(queries.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
            if (this.generation != generation || !this.plugin.featureManager().isEnabled(ID)) {
                return CompletableFuture.failedFuture(new CancellationException("Patrol was disabled or reloaded"));
            }
            List<UUID> eligible = new ArrayList<>();
            for (int i = 0, size = queries.size(); i < size; i++) {
                eligible.addAll(queries.get(i).join().players());
            }
            if (eligible.isEmpty()) return CompletableFuture.completedFuture(null);
            eligible.sort(UUID::compareTo);
            byte[][] arguments = new byte[eligible.size()][];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = UUIDUtils.toBytes(eligible.get(i));
            }
            return this.plugin.redisConnector().connection().async()
                    .<byte[]>eval(CLAIM_SCRIPT, ScriptOutputType.VALUE, new byte[][]{HISTORY_KEY}, arguments)
                    .toCompletableFuture()
                    .thenApply(selected -> targets.get(UUIDUtils.fromBytes(selected)));
        });
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onDisable() {
        this.generation++;
        return CompletableFuture.completedFuture(null);
    }

    record Target(@NotNull UUID uuid, @NotNull String name, @NotNull String server) {
    }
}