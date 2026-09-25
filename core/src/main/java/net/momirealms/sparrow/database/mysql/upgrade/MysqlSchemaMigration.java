package net.momirealms.sparrow.database.mysql.upgrade;

import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface MysqlSchemaMigration {

    // 目标版本从 2 开始, 已发布的步骤保持对应历史布局.
    int targetVersion();

    /**
     * MYSQL 数据表在版本升级过程中, 可能会执行到一半就失败. 因为 MYSQL 没有 DDL 事务.
     * 所以升级实现必须可以识别到 "上次处理到一半失败" 的状态, 并完成数据恢复.
     */
    void migrate(@NotNull Handle handle, @NotNull String prefix);
}
