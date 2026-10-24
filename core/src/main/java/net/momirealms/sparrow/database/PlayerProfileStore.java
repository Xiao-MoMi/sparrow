package net.momirealms.sparrow.database;

import net.momirealms.sparrow.database.model.PlayerProfile;
import net.momirealms.sparrow.util.ServerLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerProfileStore {

    @NotNull
    CompletableFuture<Optional<PlayerProfile>> load(@NotNull UUID player);

    @NotNull
    CompletableFuture<Void> saveLogin(@NotNull UUID player, @NotNull String name, long loginAt, @Nullable InetAddress ip);

    @NotNull
    CompletableFuture<Void> saveLogout(@NotNull UUID player, long logoutAt, @NotNull ServerLocation location);

    @NotNull
    CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name);

    @NotNull
    default CompletableFuture<Optional<String>> lookupName(@NotNull UUID player) {
        return this.load(player).thenApply(found -> found.map(PlayerProfile::name));
    }
}