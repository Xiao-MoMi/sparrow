package net.momirealms.sparrow.database.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import net.momirealms.sparrow.data.storage.DataStore;
import net.momirealms.sparrow.player.PlayerProfile;
import org.bson.Document;
import org.bson.types.Binary;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class MongoPlayerProfileStore implements DataStore<String, PlayerProfile> {
    private final MongoCollection<Document> collection;
    private final Executor executor;

    public MongoPlayerProfileStore(@NotNull MongoCollection<Document> collection, @NotNull Executor executor) {
        this.collection = collection;
        this.executor = executor;
    }

    @NotNull
    @Override
    public CompletableFuture<Map<String, PlayerProfile>> load(@NotNull UUID key) {
        return CompletableFuture.supplyAsync(() -> {
            Document document = this.collection.find(Filters.eq("_id", key)).first();
            if (document == null) return Map.of();
            Binary ip = document.get("ip", Binary.class);
            Binary location = document.get("logout_location", Binary.class);
            PlayerProfile profile = new PlayerProfile(
                    document.getString("name"),
                    document.getLong("login_at"),
                    document.getLong("logout_at"),
                    PlayerProfile.Codec.decodeAddress(ip == null ? null : ip.getData()),
                    PlayerProfile.Codec.decodeLocation(location == null ? null : location.getData())
            );
            return Map.of(PlayerProfile.ENTRY, profile);
        }, this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> save(@NotNull UUID key, @NotNull Map<String, PlayerProfile> upserts, @NotNull Set<String> removals) {
        PlayerProfile profile = upserts.get(PlayerProfile.ENTRY);
        if (profile != null) {
            return CompletableFuture.runAsync(() -> {
                Document document = new Document("_id", key)
                        .append("name", profile.name())
                        .append("login_at", profile.loginAt())
                        .append("logout_at", profile.logoutAt())
                        .append("ip", profile.ip() == null ? null : new Binary(profile.ip().getAddress()))
                        .append("logout_location", profile.logoutLocation() == null ? null : new Binary(PlayerProfile.Codec.encodeLocation(profile.logoutLocation())));
                this.collection.replaceOne(Filters.eq("_id", key), document, new ReplaceOptions().upsert(true));
            }, this.executor);
        }
        if (removals.contains(PlayerProfile.ENTRY)) {
            return CompletableFuture.runAsync(() -> this.collection.deleteOne(Filters.eq("_id", key)), this.executor);
        }
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    public CompletableFuture<Optional<UUID>> lookupUser(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> {
            Document document = this.collection.find(Filters.eq("name", name))
                    .collation(Collation.builder().locale("simple").build())
                    .sort(Sorts.descending("login_at", "_id"))
                    .projection(Projections.include("_id"))
                    .limit(1)
                    .first();
            return document == null ? Optional.empty() : Optional.of(document.get("_id", UUID.class));
        }, this.executor);
    }
}