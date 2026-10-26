package net.momirealms.sparrow.database.mysql;

import net.momirealms.sparrow.database.SqlWarpStore;
import net.momirealms.sparrow.util.UUIDUtils;
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
public final class MysqlWarpStore extends SqlWarpStore {

    public MysqlWarpStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String prefix) {
        super(sql, executor, "`" + prefix + "warps`");
    }

    @Override
    protected Object encodeUuid(UUID uuid) {
        return UUIDUtils.toBytes(uuid);
    }

    @Override
    protected int uuidSqlType() {
        return Types.BINARY;
    }

    @Nullable
    @Override
    protected UUID readUuid(ResultSet row, String column) throws SQLException {
        byte[] bytes = row.getBytes(column);
        return bytes == null ? null : UUIDUtils.fromBytes(bytes);
    }

    @Override
    protected boolean duplicateKey(SQLException exception) {
        return exception.getErrorCode() == 1062;
    }
}