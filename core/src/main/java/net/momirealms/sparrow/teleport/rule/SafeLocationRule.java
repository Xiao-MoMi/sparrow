package net.momirealms.sparrow.teleport.rule;

import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.teleport.TeleportProcessor;
import net.momirealms.sparrow.util.SparrowKey;
import net.momirealms.sparrow.util.TeleportSafety;
import net.momirealms.sparrow.util.WorldLocation;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class SafeLocationRule implements TeleportProcessor.CandidateRule {
    public static final SparrowKey TYPE = SparrowKey.sparrow("safe-location");
    private static final CompletableFuture<Component> UNSAFE = CompletableFuture.completedFuture(MessageConstants.TELEPORT_REJECTED_UNSAFE);

    @NotNull
    @Override
    public CompletableFuture<Component> check(@NotNull CandidateContext context, @NotNull WorldLocation location) {
        return TeleportSafety.isSafe(context.blocks(), context.dimensions(), location.x(), location.y(), location.z(), context.maxY(), context.position()) ? PASS : UNSAFE;
    }
}