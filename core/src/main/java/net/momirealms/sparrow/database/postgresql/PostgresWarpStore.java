package net.momirealms.sparrow.database.postgresql;

import net.momirealms.sparrow.database.SqlWarpStore;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class PostgresWarpStore extends SqlWarpStore {

    public PostgresWarpStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String prefix) {
        super(sql, executor, "\"" + prefix + "warps\"");
    }

    @Override
    protected Object encodeUuid(UUID uuid) {
        return uuid;
    }

    @Override
    protected int uuidSqlType() {
        return Types.OTHER;
    }

    @Nullable
    @Override
    protected UUID readUuid(ResultSet row, String column) throws SQLException {
        return row.getObject(column, UUID.class);
    }

    @Override
    protected boolean duplicateKey(SQLException exception) {
        return "23505".equals(exception.getSQLState());
    }
}