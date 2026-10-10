package net.momirealms.sparrow.teleport.processor;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportProcessor;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class WorldBlacklistProcessor implements TeleportProcessor.Target {
    public static final SparrowKey TYPE = SparrowKey.sparrow("world-blacklist");

    private List<String> worlds = List.of();
    private String bypassPermission = DependencyVersions.PROJECT_ID + ".bypass.teleport-world-blacklist";

    @NotNull
    @Override
    public CompletableFuture<Component> destination(@NotNull SparrowPlayer player, @NotNull Teleport teleport) {
        if (!teleport.self() || !this.worlds.contains(teleport.location().world())) return PASS;
        if (this.bypassPermission.isEmpty()) return CompletableFuture.completedFuture(MessageConstants.TELEPORT_REJECTED_WORLD);
        return player.checkPermission(this.bypassPermission).thenApply(bypass -> bypass ? null : MessageConstants.TELEPORT_REJECTED_WORLD);
    }
}
