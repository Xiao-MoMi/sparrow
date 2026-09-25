package net.momirealms.sparrow.database.postgresql.upgrade;

import org.jdbi.v3.core.Handle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface PostgresSchemaMigration {

    // 目标版本从 2 开始, 已发布的步骤保持对应历史布局.
    int targetVersion();

    /**
     * PostgreSQL 原生支持 DDL 语句的事务, 所以执行到一半失败会全量回滚.
     * 实现只需要保证 普通 DDL 和版本号一同提交即可.
     */
    void migrate(@NotNull Handle handle, @NotNull String prefix);
}
