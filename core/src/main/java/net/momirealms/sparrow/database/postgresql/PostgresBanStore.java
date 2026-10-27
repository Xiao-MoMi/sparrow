package net.momirealms.sparrow.database.postgresql;

import net.momirealms.sparrow.database.SqlBanStore;
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
public final class PostgresBanStore extends SqlBanStore {
    private final String activeConstraint;

    public PostgresBanStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String prefix) {
        super(sql, executor, "\"" + prefix + "bans\"");
        this.activeConstraint = "\"" + prefix + "bans_active_target\"";
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
    protected boolean duplicateActiveKey(SQLException exception) {
        return "23505".equals(exception.getSQLState()) && exception.getMessage().contains(this.activeConstraint);
    }
}