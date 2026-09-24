package net.momirealms.sparrow.plugin.command;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;

public interface ConfigurableCommandBuilder {

    ConfigurableCommandBuilder permission(String permission);

    ConfigurableCommandBuilder nodes(String... subNodes);

    Command.Builder<CommandSender> build();

    class BasicConfigurableCommandBuilder implements ConfigurableCommandBuilder {
        private Command.Builder<CommandSender> commandBuilder;

        public BasicConfigurableCommandBuilder(CommandManager<CommandSender> commandManager, String rootNode) {
            this.commandBuilder = commandManager.commandBuilder(rootNode);
        }

        @Override
        public ConfigurableCommandBuilder permission(String permission) {
            this.commandBuilder = this.commandBuilder.permission(permission);
            return this;
        }

        @Override
        public ConfigurableCommandBuilder nodes(String... subNodes) {
            for (String sub : subNodes) {
                this.commandBuilder = this.commandBuilder.literal(sub);
            }
            return this;
        }

        @Override
        public Command.Builder<CommandSender> build() {
            return commandBuilder;
        }
    }
}
