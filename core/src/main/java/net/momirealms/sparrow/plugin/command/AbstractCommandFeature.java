package net.momirealms.sparrow.plugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.plugin.Plugin;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.context.CommandContext;

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

    @Override
    public void handleFeedback(CommandContext<?> context, TranslatableComponent.Builder key, Component... args) {
        if (context.flags().hasFlag("silent")) return;
        commandManager.handleCommandFeedback((CommandSender) context.sender(), key, args);
    }

    @Override
    public void handleFeedback(CommandSender sender, TranslatableComponent.Builder key, Component... args) {
        commandManager.handleCommandFeedback(sender, key, args);
    }

    @Override
    public CommandManager commandManager() {
        return commandManager;
    }

    @Override
    public CommandConfig commandConfig() {
        return commandConfig;
    }

    public void setCommandConfig(CommandConfig commandConfig) {
        this.commandConfig = commandConfig;
    }

    @Override
    public Plugin plugin() {
        return plugin;
    }
}
