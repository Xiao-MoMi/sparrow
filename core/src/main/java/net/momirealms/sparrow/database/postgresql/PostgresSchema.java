package net.momirealms.sparrow.database.postgresql;

import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class PostgresSchema {
    public static final String DATA_COMPONENT = "schema";
    public static final List<String> DATA_TABLES = List.of("data");

    private PostgresSchema() {
    }

    public static void initializeData(@NotNull Handle handle, @NotNull String prefix) {
        String data = "\"" + prefix + "data\"";
        handle.execute("CREATE TABLE IF NOT EXISTS " + data + " ("
                + "uuid UUID PRIMARY KEY, name VARCHAR(64) COLLATE \"C\" NOT NULL, "
                + "login_at BIGINT NOT NULL, logout_at BIGINT NOT NULL DEFAULT 0, "
                + "ip BYTEA, logout_location BYTEA)");
    }
}