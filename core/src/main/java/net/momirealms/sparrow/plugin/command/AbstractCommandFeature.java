package net.momirealms.sparrow.plugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.plugin.Plugin;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractCommandFeature implements CommandFeature {
    protected final CommandManager commandManager;
    private final Plugin plugin;
    protected CommandConfig commandConfig;

    public AbstractCommandFeature(CommandManager commandManager, Plugin plugin) {
        this.commandManager = commandManager;
        this.plugin = plugin;
    }

    @Override
    public void registerRelatedFunctions() {
        // empty
    }

    @Override
    public void unregisterRelatedFunctions() {
        // empty
    }

    @NotNull
    protected Permission otherPermission(@NotNull Command.Builder<CommandSender> builder) {
        return Permission.allOf(builder.commandPermission(), Permission.of(this.commandConfig().getPermission() + ".other"));
    }

    @Override
    public void handleFeedback(CommandContext<?> context, TranslatableComponent key, Component... args) {
        if (context.flags().hasFlag("silent")) {
            return;
        }
        this.commandManager.handleCommandFeedback((CommandSender) context.sender(), key, args);
    }

    @Override
    public void handleFeedback(CommandSender sender, TranslatableComponent key, Component... args) {
        this.commandManager.handleCommandFeedback(sender, key, args);
    }

    @Override
    public CommandManager commandManager() {
        return this.commandManager;
    }

    @Override
    public CommandConfig commandConfig() {
        return this.commandConfig;
    }

    public void setCommandConfig(CommandConfig commandConfig) {
        this.commandConfig = commandConfig;
    }

    @Override
    public Plugin plugin() {
        return this.plugin;
    }
}
