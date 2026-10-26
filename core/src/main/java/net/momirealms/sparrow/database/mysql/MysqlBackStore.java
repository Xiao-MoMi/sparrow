package net.momirealms.sparrow.database.mysql;

import net.momirealms.sparrow.database.SqlBackStore;
import net.momirealms.sparrow.util.UUIDUtils;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public final class MysqlBackStore extends SqlBackStore {

    public MysqlBackStore(@NotNull Jdbi sql, @NotNull Executor executor, @NotNull String prefix) {
        super(sql, executor, "`" + prefix + "back`");
    }

    @Override
    protected Object encodeUuid(UUID player) {
        return UUIDUtils.toBytes(player);
    }

    @Override
    protected String upsert(String column) {
        return "INSERT INTO " + this.table + " (uuid, " + column + ") VALUES (:uuid, :location)"
                + " ON DUPLICATE KEY UPDATE " + column + " = VALUES(" + column + ")";
    }
}