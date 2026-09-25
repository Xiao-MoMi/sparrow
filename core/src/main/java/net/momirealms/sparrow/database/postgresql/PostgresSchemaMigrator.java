package net.momirealms.sparrow.database.postgresql;

import net.momirealms.sparrow.database.postgresql.upgrade.PostgresSchemaMigration;
import net.momirealms.sparrow.locale.LogConstants;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.function.BiConsumer;

@ApiStatus.Internal
public final class PostgresSchemaMigrator {
    private static final int NETWORK_TIMEOUT_MILLIS = 30 * 60 * 1000;

    private final PluginLogger logger;
    private final int currentVersion;
    private final BiConsumer<Handle, String> initializer;
    private final List<PostgresSchemaMigration> migrations;

    public PostgresSchemaMigrator(@NotNull PluginLogger logger, int currentVersion, @NotNull BiConsumer<Handle, String> initializer, @NotNull List<PostgresSchemaMigration> migrations) {
        this.logger = logger;
        this.currentVersion = currentVersion;
        this.initializer = initializer;
        this.migrations = List.copyOf(migrations);
        if (this.migrations.size() != currentVersion - 1) {
            throw new IllegalArgumentException("PostgreSQL schema migrations must cover every version from 2 through " + currentVersion);
        }
        for (int i = 0; i < this.migrations.size(); i++) {
            if (this.migrations.get(i).targetVersion() != i + 2) {
                throw new IllegalArgumentException("PostgreSQL schema migrations must cover every version in order, starting at 2");
            }
        }
    }

    // 事务锁在提交或回滚时释放, 包含 DDL 的升级也不会在连接池里遗留锁.
    public void migrate(@NotNull Jdbi jdbi, @NotNull String prefix) {
        try {
            jdbi.useHandle(handle -> {
                Connection connection = handle.getConnection();
                int originalTimeout = connection.getNetworkTimeout();
                handle.addCleanable(() -> connection.setNetworkTimeout(Runnable::run, originalTimeout));
                connection.setNetworkTimeout(Runnable::run, originalTimeout == 0 ? 0 : Math.max(originalTimeout, NETWORK_TIMEOUT_MILLIS));
                handle.useTransaction(transaction -> {
                    // 锁按当前数据库内的 schema 和表前缀区分, 获取前限制等待时间.
                    transaction.execute("SET LOCAL lock_timeout = '300s'");
                    transaction.createQuery("SELECT pg_advisory_xact_lock(1936749164, hashtext(current_schema() || ':' || :prefix))")
                            .bind("prefix", prefix).mapTo(String.class).one();
                    this.migrateLocked(transaction, prefix);
                });
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to configure the PostgreSQL schema migration connection", exception);
        }
    }

    private void migrateLocked(Handle handle, String prefix) {
        String meta = "\"" + prefix + "meta\"";
        handle.execute("CREATE TABLE IF NOT EXISTS " + meta + " (id VARCHAR(32) COLLATE \"C\" PRIMARY KEY, value BIGINT NOT NULL)");
        long stored = handle.createQuery("SELECT value FROM " + meta + " WHERE id = 'schema'").mapTo(Long.class).findOne().orElse(0L);
        if (stored < 0 || stored > this.currentVersion) {
            throw new IllegalStateException("Unsupported PostgreSQL schema version " + stored + ", supported up to " + this.currentVersion);
        }
        if (stored == 0) {
            long existing = handle.createQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name IN (<tables>)")
                    .bindList("tables", prefix + "data").mapTo(Long.class).one();
            if (existing != 0) {
                throw new IllegalStateException("PostgreSQL business tables exist without a schema version");
            }
            this.logger.info(TranslationManager.console(LogConstants.STORAGE_POSTGRESQL_SCHEMA_INITIALIZING, prefix, String.valueOf(this.currentVersion)));
            this.initializer.accept(handle, prefix);
            this.complete(handle, meta, this.currentVersion);
            return;
        }
        for (int i = (int) stored - 1; i < this.migrations.size(); i++) {
            PostgresSchemaMigration migration = this.migrations.get(i);
            int target = migration.targetVersion();
            this.logger.info(TranslationManager.console(LogConstants.STORAGE_POSTGRESQL_SCHEMA_MIGRATING, prefix, String.valueOf(target - 1), String.valueOf(target)));
            migration.migrate(handle, prefix);
            this.complete(handle, meta, target);
        }
    }

    private void complete(Handle handle, String meta, int target) {
        handle.createUpdate("INSERT INTO " + meta + " (id, value) VALUES ('schema', :version) ON CONFLICT (id) DO UPDATE SET value = EXCLUDED.value")
                .bind("version", target).execute();
    }
}
