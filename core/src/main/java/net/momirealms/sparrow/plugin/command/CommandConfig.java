package net.momirealms.sparrow.plugin.command;

import java.util.ArrayList;
import java.util.List;

public class CommandConfig {
    private boolean enable = false;
    private String permission = null;
    private List<String> usages = new ArrayList<>();

    private CommandConfig() {}

    /**
     * 创建一个完整的命令配置实例.
     *
     * @param enable 是否启用该命令功能
     * @param usages 命令用法列表, 每项通常为以 / 开头的完整命令路径
     * @param permission 执行命令所需权限节点, 允许为 null
     */
    public CommandConfig(boolean enable, List<String> usages, String permission) {
        this.enable = enable;
        this.usages = usages;
        this.permission = permission;
    }

    public boolean isEnable() {
        return enable;
    }

    public List<String> getUsages() {
        return usages;
    }

    public String getPermission() {
        return permission;
    }

    public static class Builder {
        private final CommandConfig config;

        public Builder() {
            this.config = new CommandConfig();
        }

        public Builder usages(List<String> usages) {
            config.usages = usages;
            return this;
        }

        public Builder permission(String permission) {
            config.permission = permission;
            return this;
        }

        public Builder enable(boolean enable) {
            config.enable = enable;
            return this;
        }

        public CommandConfig build() {
            return config;
        }
    }
}
