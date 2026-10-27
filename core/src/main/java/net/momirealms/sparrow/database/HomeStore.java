package net.momirealms.sparrow.database;

import net.momirealms.sparrow.data.storage.DataStore;
import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.feature.home.HomeFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface HomeStore extends DataStore<UUID, Home> {

    @NotNull
    CompletableFuture<List<UUID>> findOwners(@NotNull HomeFilter filter, @Nullable UUID after, int limit);
}