package net.momirealms.sparrow.database.postgresql;

import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class PostgresSchema {
    public static final String DATA_COMPONENT = "schema";
    public static final List<String> DATA_TABLES = List.of("data");

    public static final String NAME_HISTORY_COMPONENT = "player_name_history";
    public static final List<String> NAME_HISTORY_TABLES = List.of("player_name_history");

    public static final String IP_HISTORY_COMPONENT = "player_ip_history";
    public static final List<String> IP_HISTORY_TABLES = List.of("player_ip_history");

    public static final String BACK_COMPONENT = "back";
    public static final List<String> BACK_TABLES = List.of("back");

    private PostgresSchema() {
    }

    public static void initializeBack(@NotNull Handle handle, @NotNull String prefix) {
        handle.execute("CREATE TABLE IF NOT EXISTS \"" + prefix + "back\" ("
                + "uuid UUID PRIMARY KEY, back_location BYTEA, death_location BYTEA)");
    }

    public static void initializeData(@NotNull Handle handle, @NotNull String prefix) {
        String data = "\"" + prefix + "data\"";
        handle.execute("CREATE TABLE IF NOT EXISTS " + data + " ("
                + "uuid UUID PRIMARY KEY, name VARCHAR(64) COLLATE \"C\" NOT NULL, "
                + "login_at BIGINT NOT NULL, logout_at BIGINT NOT NULL DEFAULT 0, "
                + "ip BYTEA, logout_location BYTEA)");
    }

    public static void initializeNameHistory(@NotNull Handle handle, @NotNull String prefix) {
        String history = "\"" + prefix + "player_name_history\"";
        handle.execute("CREATE TABLE IF NOT EXISTS " + history + " ("
                + "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, uuid UUID NOT NULL, "
                + "name VARCHAR(64) COLLATE \"C\" NOT NULL, name_key VARCHAR(64) COLLATE \"C\" NOT NULL, observed_at BIGINT NOT NULL)");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "player_name_history_uuid\" ON " + history + " (uuid, id)");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "player_name_history_name\" ON " + history + " (name_key, id)");
    }

    public static void initializeIpHistory(@NotNull Handle handle, @NotNull String prefix) {
        String ipHistory = "\"" + prefix + "player_ip_history\"";
        handle.execute("CREATE TABLE IF NOT EXISTS " + ipHistory + " ("
                + "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, uuid UUID NOT NULL, "
                + "ip BYTEA NOT NULL, observed_at BIGINT NOT NULL)");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "player_ip_history_uuid\" ON " + ipHistory + " (uuid, id)");
        handle.execute("CREATE INDEX IF NOT EXISTS \"" + prefix + "player_ip_history_ip\" ON " + ipHistory + " (ip, id)");
    }
}