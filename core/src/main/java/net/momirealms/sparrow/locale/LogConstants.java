package net.momirealms.sparrow.locale;

import org.jetbrains.annotations.NotNull;

public enum LogConstants {
    PLUGIN_RESTART_AT_RUNTIME("log.plugin.restart_at_runtime"),
    PLUGIN_ENABLE_FAILED("log.plugin.enable_failed"),
    PLUGIN_SHUTDOWN_AFTER_FAILURE("log.plugin.shutdown_after_failure"),
    PLUGIN_DISABLE_AT_RUNTIME("log.plugin.disable_at_runtime"),
    PLUGIN_RELOAD_FAILED("log.plugin.reload_failed"),
    PLUGIN_RELOAD_SUBMISSION_FAILED("log.plugin.reload_submission_failed"),
    PLUGIN_INITIALIZING_PROXIES("log.plugin.initializing_proxies"),
    COMPATIBILITY_HOOKED("log.compatibility.hooked"),
    COMPATIBILITY_HOOK_FAILED("log.compatibility.hook_failed"),
    SERVER_ID_MISSING("log.server.id_missing"),
    SERVER_ID_DUPLICATE("log.server.id_duplicate"),
    PLAYER_SAVE_FAILED("log.player.save_failed"),
    PLAYER_ROSTER_REFRESH_FAILED("log.player.roster_refresh_failed"),
    TRANSLATION_DEFAULT_LOAD_FAILED("log.translation.default_load_failed"),
    TRANSLATION_DEFAULT_SYNTAX_ERROR("log.translation.default_syntax_error"),
    TRANSLATION_INVALID_FILE("log.translation.invalid_file"),
    TRANSLATION_READ_FAILED("log.translation.read_failed"),
    TRANSLATION_DIRECTORY_FAILED("log.translation.directory_failed"),
    TRANSLATION_LOCALE_MISSING("log.translation.locale_missing"),
    STORAGE_STALE_INDEX_DROPPED("log.storage.stale_index_dropped"),
    STORAGE_SCHEMA_TOO_NEW("log.storage.schema_too_new"),
    STORAGE_MYSQL_SCHEMA_INITIALIZING("log.storage.mysql_schema_initializing"),
    STORAGE_MYSQL_SCHEMA_MIGRATING("log.storage.mysql_schema_migrating"),
    STORAGE_MYSQL_VERSION_UNSUPPORTED("log.storage.mysql_version_unsupported"),
    STORAGE_POSTGRESQL_SCHEMA_INITIALIZING("log.storage.postgresql_schema_initializing"),
    STORAGE_POSTGRESQL_SCHEMA_MIGRATING("log.storage.postgresql_schema_migrating"),
    REDIS_VERSION_UNSUPPORTED("log.redis.version_unsupported"),
    REDIS_VERSION_CHECK_FAILED("log.redis.version_check_failed");

    private final String key;

    LogConstants(@NotNull String key) {
        this.key = key;
    }

    @NotNull
    public String key() {
        return this.key;
    }
}
