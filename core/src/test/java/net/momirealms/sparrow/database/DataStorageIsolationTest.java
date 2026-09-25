package net.momirealms.sparrow.database;

import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DataStorageIsolationTest {
    private static final String MANAGER = "net.momirealms.sparrow.database.DataStorage";

    @Test
    void mysqlManagerLoadsWithoutOtherDrivers() throws Exception {
        assertManagerLoads(DatabaseType.MYSQL, "com.mongodb.", "org.bson.", "org.mariadb.", "org.postgresql.");
    }

    @Test
    void mariaDbManagerLoadsWithoutMysqlOrPostgresDrivers() throws Exception {
        assertManagerLoads(DatabaseType.MARIADB, "com.mongodb.", "org.bson.", "com.mysql.", "org.postgresql.");
    }

    @Test
    void postgresManagerLoadsWithoutMysqlOrMariaDbDrivers() throws Exception {
        assertManagerLoads(DatabaseType.POSTGRESQL, "com.mongodb.", "org.bson.", "com.mysql.", "org.mariadb.");
    }

    @Test
    void mongoManagerLoadsWithoutSqlLibraries() throws Exception {
        assertManagerLoads(DatabaseType.MONGODB, "org.jdbi.", "com.zaxxer.", "com.mysql.", "org.mariadb.", "org.postgresql.");
    }

    private static void assertManagerLoads(DatabaseType type, String... excludedPackages) throws Exception {
        ClassLoader loader = new ClassLoader(DataStorageIsolationTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                for (String excluded : excludedPackages) {
                    if (name.startsWith(excluded)) throw new ClassNotFoundException(name);
                }
                if (!name.startsWith("net.momirealms.sparrow.database.") || name.equals(DatabaseType.class.getName())) {
                    return super.loadClass(name, resolve);
                }
                synchronized (this.getClassLoadingLock(name)) {
                    Class<?> loaded = this.findLoadedClass(name);
                    if (loaded == null) {
                        try (InputStream stream = this.getResourceAsStream(name.replace('.', '/') + ".class")) {
                            byte[] bytes = stream.readAllBytes();
                            loaded = this.defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException exception) {
                            throw new ClassNotFoundException(name, exception);
                        }
                    }
                    if (resolve) this.resolveClass(loaded);
                    return loaded;
                }
            }
        };
        PluginConfig.DatabaseOptions options = new PluginConfig.DatabaseOptions();
        var typeField = PluginConfig.DatabaseOptions.class.getDeclaredField("type");
        typeField.setAccessible(true);
        typeField.set(options, type);
        Class<?> managerClass = Class.forName(MANAGER, true, loader);
        Object manager = managerClass.getMethod("create", PluginConfig.DatabaseOptions.class, Executor.class, PluginLogger.class).invoke(null, options, (Executor) Runnable::run, mock(PluginLogger.class));
        assertEquals(switch (type) {
            case MYSQL -> "net.momirealms.sparrow.database.mysql.MysqlDataStorage";
            case MARIADB -> "net.momirealms.sparrow.database.mariadb.MariaDbDataStorage";
            case POSTGRESQL -> "net.momirealms.sparrow.database.postgresql.PostgresDataStorage";
            case MONGODB -> "net.momirealms.sparrow.database.mongo.MongoDataStorage";
        }, manager.getClass().getName());
    }
}
