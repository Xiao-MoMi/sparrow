package net.momirealms.sparrow.database;

import com.zaxxer.hikari.HikariDataSource;
import net.momirealms.sparrow.database.mysql.MysqlSchemaMigrator;
import net.momirealms.sparrow.database.postgresql.PostgresSchemaMigrator;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.world.WorldLocation;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.junit.jupiter.api.Assumptions;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.Handle;
import net.momirealms.sparrow.database.mysql.upgrade.MysqlSchemaMigration;
import net.momirealms.sparrow.database.postgresql.upgrade.PostgresSchemaMigration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SqlDataStorageTest {
    @Test
    void postgresPersistsLocationsAndChecksSchema() throws Exception {
        this.verifyStorage(DatabaseType.POSTGRESQL, "jdbc:postgresql://localhost:5432/postgres?connectTimeout=2", "postgres");
    }

    @Test
    void mariaDbPersistsLocationsAndChecksSchema() throws Exception {
        this.verifyStorage(DatabaseType.MARIADB, "jdbc:mariadb://localhost:3307/mysql?connectTimeout=2000", "root");
    }

    @Test
    void mysqlPersistsLocationsAndChecksSchema() throws Exception {
        this.verifyStorage(DatabaseType.MYSQL, "jdbc:mysql://localhost:3306/mysql?connectTimeout=2000", "root");
    }

    private void verifyStorage(DatabaseType type, String defaultUrl, String defaultUser) throws Exception {
        String env = "SPARROW_TEST_" + type.name();
        String url = System.getenv().getOrDefault(env + "_URL", defaultUrl);
        String user = System.getenv().getOrDefault(env + "_USERNAME", defaultUser);
        String password = System.getenv().getOrDefault(env + "_PASSWORD", "");
        try (var connection = DriverManager.getConnection(url, user, password)) {
            var metadata = connection.getMetaData();
            String product = metadata.getDatabaseProductName();
            assertEquals(switch (type) {
                case MYSQL -> "MySQL";
                case MARIADB -> "MariaDB";
                case POSTGRESQL -> "PostgreSQL";
                default -> throw new AssertionError(type);
            }, product);
            System.out.println(product + " integration server: " + metadata.getDatabaseProductVersion());
        } catch (SQLException unavailable) {
            Assumptions.abort("Local " + type + " test connection is unavailable");
        }
        PluginConfig.DatabaseOptions options = new PluginConfig.DatabaseOptions();
        set(options, "type", type);
        PluginConfig.SqlOptions selected = switch (type) {
            case POSTGRESQL -> options.postgresql();
            case MARIADB -> options.mariadb();
            default -> options.mysql();
        };
        String prefix = "test_" + UUID.randomUUID().toString().replace("-", "") + "_";
        Field prefixField = PluginConfig.SqlOptions.class.getDeclaredField("tablePrefix");
        prefixField.setAccessible(true);
        prefixField.set(selected, prefix);
        String quote = type == DatabaseType.POSTGRESQL ? "\"" : "`";
        String table = quote + prefix + "data" + quote;
        String meta = quote + prefix + "meta" + quote;
        try (var translations = mockStatic(TranslationManager.class);
             var pools = mockConstruction(HikariDataSource.class, (pool, context) -> when(pool.getConnection()).thenAnswer(ignored -> DriverManager.getConnection(url, user, password)))) {
            DataStorage storage = DataStorage.create(options, Runnable::run, mock(PluginLogger.class));
            storage.initialize();
            String driver = switch (type) {
                case MYSQL -> "mysql.cj.jdbc.Driver";
                case MARIADB -> "mariadb.Driver";
                case POSTGRESQL -> "postgresql.Driver";
                default -> throw new AssertionError(type);
            };
            verify(pools.constructed().getFirst()).setDriverClassName(DependencyVersions.PROJECT_PACKAGE + ".libraries." + driver);
            try {
                UUID uuid = UUID.randomUUID();
                WorldLocation location = new WorldLocation("world", -34.75, 66.25, 18.5, 90.25f, -12.5f);
                storage.saveLogin(uuid, "First", 100).join();
                storage.saveLogout(uuid, "First", 200, "survival", location).join();
                storage.saveLogin(uuid, "Renamed", 300).join();
                storage.saveLogout(uuid, "Old", 150, "old", new WorldLocation("old", 0, 0, 0, 0, 0)).join();
                storage.saveLogin(uuid, "Old", 50).join();
                assertEquals(new PlayerData(uuid, "Renamed", 300, 200, "survival", location, 300), storage.loadPlayer(uuid).join().orElseThrow());
                assertEquals(uuid, storage.lookupUser("Renamed").join().orElseThrow());
                assertEquals("Renamed", storage.lookupName(uuid).join().orElseThrow());
                assertTrue(storage.lookupUser("renamed").join().isEmpty());
                try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                    try (var result = statement.executeQuery("SELECT " + quote + "value" + quote + " FROM " + meta + " WHERE id = 'schema'")) {
                        assertTrue(result.next());
                        assertEquals(1, result.getInt(1));
                    }
                    this.upgrade(Jdbi.create(url, user, password), prefix, type == DatabaseType.POSTGRESQL);
                    assertThrows(IllegalStateException.class, storage::initialize);
                }
            } finally {
                storage.close();
            }
        } finally {
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS " + table);
                statement.execute("DROP TABLE IF EXISTS " + meta);
            }
        }
    }

    private void upgrade(Jdbi jdbi, String prefix, boolean postgres) {
        PluginLogger logger = mock(PluginLogger.class);
        AtomicBoolean interrupted = new AtomicBoolean();
        Runnable migrate;
        if (postgres) {
            PostgresSchemaMigrator migrator = new PostgresSchemaMigrator(logger, 2, (handle, tablePrefix) -> fail("Expected upgrade"), List.of(new PostgresSchemaMigration() {
                @Override
                public int targetVersion() {
                    return 2;
                }

                @Override
                public void migrate(Handle handle, String tablePrefix) {
                    handle.execute("ALTER TABLE \"" + tablePrefix + "data\" ADD COLUMN upgraded INT");
                    if (!interrupted.getAndSet(true)) throw new IllegalStateException("test interruption");
                }
            }));
            migrate = () -> migrator.migrate(jdbi, prefix);
        } else {
            MysqlSchemaMigrator migrator = new MysqlSchemaMigrator(logger, 2, (handle, tablePrefix) -> fail("Expected upgrade"), List.of(new MysqlSchemaMigration() {
                @Override
                public int targetVersion() {
                    return 2;
                }

                @Override
                public void migrate(Handle handle, String tablePrefix) {
                    long columns = handle.createQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=:table AND column_name='upgraded'")
                            .bind("table", tablePrefix + "data").mapTo(Long.class).one();
                    if (columns == 0) handle.execute("ALTER TABLE `" + tablePrefix + "data` ADD COLUMN upgraded INT");
                    if (!interrupted.getAndSet(true)) throw new IllegalStateException("test interruption");
                }
            }));
            migrate = () -> migrator.migrate(jdbi, prefix);
        }
        assertThrows(IllegalStateException.class, migrate::run);
        String meta = postgres ? "\"" + prefix + "meta\"" : "`" + prefix + "meta`";
        String column = postgres ? "value" : "`value`";
        assertEquals(1L, jdbi.withHandle(handle -> handle.createQuery("SELECT " + column + " FROM " + meta + " WHERE id='schema'").mapTo(Long.class).one()).longValue());
        if (!postgres) {
            assertEquals(2L, jdbi.withHandle(handle -> handle.createQuery("SELECT " + column + " FROM " + meta + " WHERE id='schema_pending'").mapTo(Long.class).one()).longValue());
        }
        migrate.run();
        assertEquals(2L, jdbi.withHandle(handle -> handle.createQuery("SELECT " + column + " FROM " + meta + " WHERE id='schema'").mapTo(Long.class).one()).longValue());
        assertEquals(0L, jdbi.withHandle(handle -> handle.createQuery("SELECT COUNT(*) FROM " + meta + " WHERE id='schema_pending'").mapTo(Long.class).one()).longValue());
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
