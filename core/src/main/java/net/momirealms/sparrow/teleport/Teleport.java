package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.teleport.position.Fixed;
import net.momirealms.sparrow.teleport.position.TeleportPosition;
import net.momirealms.sparrow.teleport.processor.target.PositionProcessor;
import net.momirealms.sparrow.teleport.processor.post.PostProcessor;
import net.momirealms.sparrow.util.WorldLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class Teleport {
    private @Nullable Supplier<CompletionStage<String>> server;
    private @Nullable TeleportPosition position;
    private @Nullable TeleportContext context;
    private final List<PositionProcessor> targetProcessors = new ArrayList<>();
    private final List<PostProcessor> postProcessors = new ArrayList<>();

    private Teleport() {
    }

    @NotNull
    public static Teleport builder() {
        return new Teleport();
    }

    @NotNull
    public Teleport server(@NotNull String server) {
        this.server = () -> CompletableFuture.completedFuture(server);
        return this;
    }

    @NotNull
    public Teleport server(@NotNull Supplier<String> server) {
        this.server = () -> CompletableFuture.completedFuture(server.get());
        return this;
    }

    @NotNull
    public Teleport server(@NotNull CompletionStage<String> server) {
        this.server = () -> server;
        return this;
    }

    @NotNull
    public Teleport position(@NotNull String world, double x, double y, double z) {
        return this.position(world, x, y, z, 0.0F, 0.0F);
    }

    @NotNull
    public Teleport position(@NotNull String world, double x, double y, double z, float yaw, float pitch) {
        return this.position(new Fixed(new WorldLocation(world, x, y, z, yaw, pitch)));
    }

    @NotNull
    public Teleport position(@NotNull TeleportPosition position) {
        this.position = position;
        return this;
    }

    @NotNull
    public Teleport context(@NotNull TeleportContext context) {
        this.context = context;
        return this;
    }

    @NotNull
    public Teleport targetProcessor(@NotNull PositionProcessor processor) {
        this.targetProcessors.add(processor);
        return this;
    }

    @NotNull
    public Teleport postProcessor(@NotNull PostProcessor processor) {
        this.postProcessors.add(processor);
        return this;
    }

    @NotNull
    CompletionStage<String> resolveServer() {
        if (this.server == null) throw new IllegalStateException("Teleport server is not specified");
        return this.server.get();
    }

    @NotNull
    TeleportPosition getPosition() {
        if (this.position == null) throw new IllegalStateException("Teleport position is not specified");
        return this.position;
    }

    @NotNull
    TeleportContext createContext(@NotNull BukkitSparrowPlayer player) {
        TeleportContext current = this.context == null ? new TeleportContext() : this.context;
        current.bindPlayer(player);
        return current;
    }

    @NotNull
    List<PositionProcessor> targetProcessors() {
        return this.targetProcessors;
    }

    @NotNull
    List<PostProcessor> postProcessors() {
        return this.postProcessors;
    }
}