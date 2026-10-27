package net.momirealms.sparrow.database.mysql;

import net.momirealms.sparrow.database.SqlBanStore;
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
public final class MysqlBanStore extends SqlBanStore {

    public MysqlBanStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String prefix) {
        super(sql, executor, "`" + prefix + "bans`");
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
        byte[] value = row.getBytes(column);
        return value == null ? null : UUIDUtils.fromBytes(value);
    }

    @Override
    protected boolean duplicateActiveKey(SQLException exception) {
        return exception.getErrorCode() == 1062 && (exception.getMessage().contains("'bans_active_target'") || exception.getMessage().contains(".bans_active_target'"));
    }
}