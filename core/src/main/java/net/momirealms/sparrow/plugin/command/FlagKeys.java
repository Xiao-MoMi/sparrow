package net.momirealms.sparrow.plugin.command;

import org.incendo.cloud.parser.flag.CommandFlag;

public final class FlagKeys {
    private FlagKeys() {}

    // 静默模式
    public static final String SILENT = "silent";
    public static final CommandFlag<Void> SILENT_FLAG = CommandFlag.builder(SILENT).withAliases("s").build();
}
