package net.momirealms.sparrow.plugin.command;

import net.momirealms.sparrow.yaml.serializer.auto.annotation.Comment;
import java.util.ArrayList;
import java.util.List;

public class CommandConfig {
    @Comment("Whether to register this command.")
    @Comment(lang = "zh", value = "是否注册此命令.")
    private boolean enable = false;
    @Comment("Permission required to execute this command.")
    @Comment(lang = "zh", value = "执行此命令需要的权限.")
    private String permission = null;
    @Comment("Command paths, each starting with a slash.")
    @Comment(lang = "zh", value = "命令用法列表, 每项以斜杠开头.")
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
