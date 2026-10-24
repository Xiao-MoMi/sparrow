package net.momirealms.sparrow.database;

import net.momirealms.sparrow.player.PlayerProfile;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerProfileStore {

    @NotNull
    CompletableFuture<Optional<PlayerProfile>> load(@NotNull UUID player);

    @NotNull
    CompletableFuture<Void> save(@NotNull UUID player, @NotNull PlayerProfile profile);

    @NotNull
    CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name);

    @NotNull
    default CompletableFuture<Optional<String>> lookupName(@NotNull UUID player) {
        return this.load(player).thenApply(found -> found.map(PlayerProfile::name));
    }
}