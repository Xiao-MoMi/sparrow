package net.momirealms.sparrow.database.postgresql;

import net.momirealms.sparrow.database.SqlBackStore;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class PostgresBackStore extends SqlBackStore {

    public PostgresBackStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String prefix) {
        super(sql, executor, "\"" + prefix + "back\"");
    }

    @Override
    protected Object encodeUuid(UUID player) {
        return player;
    }

    @Override
    protected String upsert(String column) {
        return "INSERT INTO " + this.table + " (uuid, " + column + ") VALUES (:uuid, :location)"
                + " ON CONFLICT (uuid) DO UPDATE SET " + column + " = EXCLUDED." + column;
    }
}