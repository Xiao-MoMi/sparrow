package net.momirealms.sparrow.database.mysql;

import net.momirealms.sparrow.database.mysql.upgrade.MysqlSchemaMigration;
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

// 初始化当前 MySQL 表结构或逐级升级旧库, 并协调多个服务器同时启动时的迁移顺序.
// meta 中的 schema 表示完成的版本, schema_pending 表示已经开始但尚未公布完成的版本.
@ApiStatus.Internal
public final class MysqlSchemaMigrator {
    private static final int LOCK_WAIT_SECONDS = 300;
    private static final int NETWORK_TIMEOUT_MILLIS = 30 * 60 * 1000;

    private final PluginLogger logger;
    private final int currentVersion;
    private final BiConsumer<Handle, String> initializer; // 在已准备 meta 的空库上直接创建当前完整结构
    private final List<MysqlSchemaMigration> migrations; // 从目标版本 2 开始连续排列的旧库迁移, 构造后固定

    /**
     * 为当前完整结构和旧库升级链创建启动入口.
     *
     * @param logger 输出初始化与每步迁移开始提示的日志入口
     * @param currentVersion 当前完整表结构的版本, 至少为 1
     * @param initializer 创建当前结构的动作, 同一目标版本中断后可重入
     * @param migrations 按目标版本升序排列的迁移
     */
    public MysqlSchemaMigrator(@NotNull PluginLogger logger, int currentVersion, @NotNull BiConsumer<Handle, String> initializer, @NotNull List<MysqlSchemaMigration> migrations) {
        this.logger = logger;
        this.currentVersion = currentVersion;
        this.initializer = initializer;
        this.migrations = List.copyOf(migrations);
        if (this.migrations.size() != currentVersion - 1) {
            throw new IllegalArgumentException("MySQL schema migrations must cover every version from 2 through " + currentVersion);
        }
        for (int i = 0; i < this.migrations.size(); i++) {
            if (this.migrations.get(i).targetVersion() != i + 2) {
                throw new IllegalArgumentException("MySQL schema migrations must cover every version in order, starting at 2");
            }
        }
    }

    // 将指定表前缀下的数据升级到当前代码支持的最新版本.
    public void migrate(@NotNull Jdbi jdbi, @NotNull String prefix) {
        // 命名锁归属于数据库连接, 获取、迁移和释放必须使用同一个 Handle.
        try {
            jdbi.useHandle(handle -> {
                Connection connection = handle.getConnection();
                int originalTimeout = connection.getNetworkTimeout();
                // 迁移可以执行长 DDL, 连接归还前恢复业务超时; 用户设置的无限或更长等待保持有效.
                handle.addCleanable(() -> connection.setNetworkTimeout(Runnable::run, originalTimeout));
                connection.setNetworkTimeout(Runnable::run, originalTimeout == 0 ? 0 : Math.max(originalTimeout, NETWORK_TIMEOUT_MILLIS));
                // 命名锁跨 DDL 的隐式提交保持有效; 摘要让数据库名与前缀组成的锁名落在 64 字符上限内.
                String lock = handle.createQuery("SELECT SHA2(CONCAT('sparrow-schema:', DATABASE(), ':', :prefix), 256)").bind("prefix", prefix).mapTo(String.class).one();
                Integer acquired = handle.createQuery("SELECT GET_LOCK(:name, :seconds)").bind("name", lock).bind("seconds", LOCK_WAIT_SECONDS).mapTo(Integer.class).one();
                if (!Integer.valueOf(1).equals(acquired)) {
                    throw new IllegalStateException("Could not acquire the MySQL schema migration lock within " + LOCK_WAIT_SECONDS + " seconds");
                }
                try {
                    this.migrateLocked(handle, prefix);
                } finally {
                    handle.createQuery("SELECT RELEASE_LOCK(:name)").bind("name", lock).mapTo(Integer.class).one();
                }
            });
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to configure the MySQL schema migration connection", exception);
        }
    }

    // 在独占迁移期间按版本记录选择初始化或旧库升级.
    private void migrateLocked(Handle handle, String prefix) {
        // meta 是迁移管线的基础, 在业务表创建前就需要保存初始化目标.
        String meta = "`" + prefix + "meta`";
        handle.execute("CREATE TABLE IF NOT EXISTS " + meta + " (`id` VARCHAR(32) NOT NULL PRIMARY KEY, `value` BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
        long stored = handle.createQuery("SELECT `value` FROM " + meta + " WHERE `id` = 'schema'").mapTo(Long.class).findOne().orElse(0L);
        Long pending = handle.createQuery("SELECT `value` FROM " + meta + " WHERE `id` = 'schema_pending'").mapTo(Long.class).findOne().orElse(null);
        // 较新的已完成版本或进行中版本都要求相应的迁移代码参与恢复.
        if (stored < 0 || stored > this.currentVersion) {
            throw new IllegalStateException("Unsupported MySQL schema version " + stored + ", supported up to " + this.currentVersion);
        }
        if (stored == 0) {
            // 初始化中断时, 必须由相同目标版本的完整建表定义恢复.
            if (pending != null && pending != this.currentVersion) {
                throw new IllegalStateException("Unfinished MySQL schema initialization targets version " + pending + ", current version is " + this.currentVersion + "; finish initialization with the matching plugin version");
            }
            if (pending == null) {
                long existing = handle.createQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN (<tables>)")
                        .bindList("tables", prefix + "data").mapTo(Long.class).one();
                if (existing != 0) {
                    throw new IllegalStateException("MySQL business tables exist without a schema version");
                }
            }
            this.logger.info(TranslationManager.console(LogConstants.STORAGE_MYSQL_SCHEMA_INITIALIZING, prefix, String.valueOf(this.currentVersion)));
            this.markPending(handle, meta, this.currentVersion);
            this.initializer.accept(handle, prefix);
            this.complete(handle, meta, this.currentVersion);
            return;
        }
        if (pending != null && (pending != stored + 1 || pending > this.currentVersion)) {
            throw new IllegalStateException("Unsupported pending MySQL schema migration " + pending + " after version " + stored);
        }
        if (stored == this.currentVersion) return;
        // 目标版本 2 位于下标 0, 从已完成版本的下一步开始执行.
        for (int i = (int) stored - 1; i < this.migrations.size(); i++) {
            MysqlSchemaMigration migration = this.migrations.get(i);
            int target = migration.targetVersion();
            this.logger.info(TranslationManager.console(LogConstants.STORAGE_MYSQL_SCHEMA_MIGRATING, prefix, String.valueOf(target - 1), String.valueOf(target)));
            this.markPending(handle, meta, target);
            migration.migrate(handle, prefix);
            this.complete(handle, meta, target);
        }
    }

    // 在执行 DDL 前持久化目标版本, 供失败重启时识别未完成的布局.
    private void markPending(Handle handle, String meta, int target) {
        handle.createUpdate("INSERT INTO " + meta + " (`id`, `value`) VALUES ('schema_pending', :version) ON DUPLICATE KEY UPDATE `value` = :version")
                .bind("version", target).execute();
    }

    // DDL 已完成, 用一个 DML 事务同时公布版本并清除进行中标记.
    private void complete(Handle handle, String meta, int target) {
        handle.useTransaction(transaction -> {
            transaction.createUpdate("INSERT INTO " + meta + " (`id`, `value`) VALUES ('schema', :version) ON DUPLICATE KEY UPDATE `value` = :version")
                    .bind("version", target).execute();
            transaction.execute("DELETE FROM " + meta + " WHERE `id` = 'schema_pending'");
        });
    }
}
