package net.momirealms.sparrow.database.mysql;

import net.momirealms.sparrow.database.SqlHomeStore;
import net.momirealms.sparrow.util.UUIDUtils;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class MysqlHomeStore extends SqlHomeStore {

    public MysqlHomeStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String prefix) {
        super(sql, executor, "`" + prefix + "homes`");
    }

    @Override
    protected Object encodeUuid(UUID uuid) {
        return UUIDUtils.toBytes(uuid);
    }

    @Override
    protected UUID readUuid(ResultSet row, String column) throws SQLException {
        return UUIDUtils.fromBytes(row.getBytes(column));
    }
}