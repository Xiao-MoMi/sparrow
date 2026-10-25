package net.momirealms.sparrow.service.cooldown;

import io.lettuce.core.SetArgs;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CooldownService {
    private static final String KEY_PREFIX = "sparrow:cooldown:"; // 后接冷却 ID 与玩家 UUID, 键过期即冷却结束
    private static final byte[] MARKER = {1};

    private final SparrowPlugin plugin = SparrowPlugin.instance();

    // 没有冷却时返回 Duration.ZERO
    @NotNull
    public CompletableFuture<Duration> remaining(@NotNull UUID player, @NotNull String id) {
        return this.plugin.redisConnector().connection().async()
                .pttl(key(player, id))
                .toCompletableFuture()
                .thenApply(millis -> millis > 0 ? Duration.ofMillis(millis) : Duration.ZERO);
    }

    // 时长为 0 时不写入
    @NotNull
    public CompletableFuture<Void> start(@NotNull UUID player, @NotNull String id, @NotNull Duration duration) {
        if (duration.isZero()) return CompletableFuture.completedFuture(null);
        return this.plugin.redisConnector().connection().async()
                .set(key(player, id), MARKER, SetArgs.Builder.px(duration.toMillis()))
                .toCompletableFuture()
                .thenApply(ignored -> null);
    }

    private static byte[] key(UUID player, String id) {
        return (KEY_PREFIX + id + ":" + player).getBytes(StandardCharsets.UTF_8);
    }
}