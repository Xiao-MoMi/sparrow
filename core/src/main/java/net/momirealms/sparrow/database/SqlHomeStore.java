package net.momirealms.sparrow.database;

import net.momirealms.sparrow.feature.home.Home;
import net.momirealms.sparrow.feature.home.HomeFilter;
import net.momirealms.sparrow.util.WorldLocation;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.Query;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public abstract class SqlHomeStore implements HomeStore {
    private final Jdbi sql;
    private final Executor executor;
    private final String table;

    protected SqlHomeStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String table) {
        this.sql = sql;
        this.executor = executor;
        this.table = table;
    }

    @NotNull
    @Override
    public CompletableFuture<List<UUID>> findOwners(@NotNull HomeFilter filter, @Nullable UUID after, int limit) {
        List<String> conditions = new ArrayList<>();
        if (filter.owner() != null) {
            conditions.add("owner = :owner");
        }
        if (filter.server() != null) {
            conditions.add("server = :server");
        }
        if (filter.world() != null) {
            conditions.add("world = :world");
        }
        if (after != null) {
            conditions.add("owner > :after");
        }
        String sql = "SELECT DISTINCT owner FROM " + this.table + " WHERE " + String.join(" AND ", conditions) + " ORDER BY owner LIMIT :limit";
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> {
            Query query = handle.createQuery(sql).bind("limit", limit);
            if (filter.owner() != null) {
                query.bind("owner", this.encodeUuid(filter.owner()));
            }
            if (filter.server() != null) {
                query.bind("server", filter.server());
            }
            if (filter.world() != null) {
                query.bind("world", filter.world());
            }
            if (after != null) {
                query.bind("after", this.encodeUuid(after));
            }
            return query.map((row, context) -> this.readUuid(row, "owner")).list();
        }), this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Map<UUID, Home>> load(@NotNull UUID owner) {
        String query = "SELECT id, name, server, world, x, y, z, yaw, pitch, created_at, updated_at FROM " + this.table + " WHERE owner = :owner";
        return CompletableFuture.supplyAsync(() -> this.sql.withHandle(handle -> {
            List<Home> homes = handle.createQuery(query)
                    .bind("owner", this.encodeUuid(owner))
                    .map((row, context) -> this.read(row, owner))
                    .list();
            Map<UUID, Home> entries = new HashMap<>(homes.size());
            for (int i = 0, size = homes.size(); i < size; i++) {
                Home home = homes.get(i);
                entries.put(home.id(), home);
            }
            return entries;
        }), this.executor);
    }

    @NotNull
    @Override
    public CompletableFuture<Void> save(@NotNull UUID owner, @NotNull Map<UUID, Home> upserts, @NotNull Set<UUID> removals) {
        if (upserts.isEmpty() && removals.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> this.sql.useTransaction(handle -> {
            Set<UUID> changed = new HashSet<>(removals);
            changed.addAll(upserts.keySet());
            // 同批次的最终名称可能互换, 先释放受影响记录的旧名称, 再插入最终值; 事务外只看到提交后的结果.
            handle.createUpdate("DELETE FROM " + this.table + " WHERE owner = :owner AND id IN (<ids>)")
                    .bind("owner", this.encodeUuid(owner))
                    .bindList("ids", changed.stream().map(this::encodeUuid).toList())
                    .execute();
            if (upserts.isEmpty()) return;
            String insert = "INSERT INTO " + this.table + " (id, owner, name, name_key, server, world, x, y, z, yaw, pitch, created_at, updated_at)"
                    + " VALUES (:id, :owner, :name, :name_key, :server, :world, :x, :y, :z, :yaw, :pitch, :created_at, :updated_at)";
            try (PreparedBatch batch = handle.prepareBatch(insert)) {
                for (Map.Entry<UUID, Home> entry : upserts.entrySet()) {
                    Home home = entry.getValue();
                    WorldLocation location = home.location();
                    batch.bind("id", this.encodeUuid(entry.getKey()))
                            .bind("owner", this.encodeUuid(owner))
                            .bind("name", home.name())
                            .bind("name_key", home.getNameKey())
                            .bind("server", home.server())
                            .bind("world", location.world())
                            .bind("x", location.x())
                            .bind("y", location.y())
                            .bind("z", location.z())
                            .bind("yaw", location.yaw())
                            .bind("pitch", location.pitch())
                            .bind("created_at", home.createdAt())
                            .bind("updated_at", home.updatedAt())
                            .add();
                }
                batch.execute();
            }
        }), this.executor);
    }

    private Home read(ResultSet row, UUID owner) throws SQLException {
        return new Home(
                this.readUuid(row, "id"),
                owner,
                row.getString("name"),
                row.getString("server"),
                new WorldLocation(row.getString("world"), row.getDouble("x"), row.getDouble("y"), row.getDouble("z"), row.getFloat("yaw"), row.getFloat("pitch")),
                row.getLong("created_at"),
                row.getLong("updated_at")
        );
    }

    protected abstract Object encodeUuid(UUID uuid);

    protected abstract UUID readUuid(ResultSet row, String column) throws SQLException;
}