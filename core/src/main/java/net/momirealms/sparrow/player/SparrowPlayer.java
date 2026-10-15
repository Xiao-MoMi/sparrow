package net.momirealms.sparrow.player;

import net.momirealms.sparrow.database.PlayerData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SparrowPlayer {

    @NotNull
    UUID uniqueId();

    @NotNull
    String name();

    boolean isOnlineLocally();

    boolean isOnlineGlobally();

    @Nullable
    String getCurrentServerId();

    @NotNull
    CompletableFuture<Boolean> checkPermission(@NotNull String permission);

    @NotNull
    CompletableFuture<Optional<PlayerData>> loadData();
}