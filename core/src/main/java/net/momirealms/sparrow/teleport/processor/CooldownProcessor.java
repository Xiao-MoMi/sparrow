package net.momirealms.sparrow.teleport.processor;

import io.lettuce.core.SetArgs;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportProcessor;
import net.momirealms.sparrow.teleport.TeleportResult;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class CooldownProcessor implements TeleportProcessor.Pre {
    private static final String KEY_PREFIX = "sparrow:teleport-cooldown:"; // 后接冷却 ID 与玩家 UUID, 过期即冷却结束

    private String id = "";
    private int seconds = 0;
    private String bypassPermission = DependencyVersions.PROJECT_ID + ".bypass.teleport-cooldown";

    CooldownProcessor() {
    }

    public CooldownProcessor(@NotNull String id) {
        this.id = id;
    }

    @Override
    public void validate() {
        if (this.id.isEmpty()) throw new IllegalArgumentException("cooldown requires an id");
    }

    @NotNull
    @Override
    public CompletableFuture<Component> before(@NotNull BukkitSparrowPlayer player, @NotNull Teleport teleport) {
        if (this.seconds <= 0 || !teleport.self()) return PASS;
        if (!this.bypassPermission.isEmpty() && player.hasPermission(this.bypassPermission)) return PASS;
        return SparrowPlugin.instance().redisConnector().connection().async()
                .pttl(this.key(teleport))
                .toCompletableFuture()
                .thenApply(millis -> millis > 0 ? MessageConstants.TELEPORT_COOLDOWN.arguments(Component.text((millis + 999) / 1000)) : null);
    }

    @Override
    public void finished(@NotNull Teleport teleport, @NotNull TeleportResult result) {
        if (this.seconds <= 0 || !teleport.self()) return;
        if (result != TeleportResult.LOCAL_SUCCESS && result != TeleportResult.REMOTE_SUCCESS) return;
        SparrowPlugin.instance().redisConnector().connection().async().set(this.key(teleport), new byte[]{1}, SetArgs.Builder.px(this.seconds * 1000L));
    }

    private byte[] key(Teleport teleport) {
        return (KEY_PREFIX + this.id + ":" + teleport.player()).getBytes(StandardCharsets.UTF_8);
    }
}