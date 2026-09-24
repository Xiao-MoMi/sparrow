package net.momirealms.sparrow.database;

import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataStorageIsolationTest {
    private static final String MANAGER = "net.momirealms.sparrow.database.DataStorage";

    @Test
    void sqlManagerLoadsWithoutMongoLibraries() throws Exception {
        assertManagerLoads(DatabaseType.MYSQL, "com.mongodb.", "org.bson.");
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
                if (!name.equals(MANAGER) && !name.startsWith(MANAGER + "$")
                        && !name.equals(MANAGER.replace("DataStorage", "SqlDataStorage"))
                        && !name.equals(MANAGER.replace("DataStorage", "MongoDataStorage"))) {
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
        Object manager = managerClass.getMethod("create", PluginConfig.DatabaseOptions.class, Executor.class).invoke(null, options, (Executor) Runnable::run);
        assertEquals(type, managerClass.getMethod("type").invoke(manager));
    }
}
