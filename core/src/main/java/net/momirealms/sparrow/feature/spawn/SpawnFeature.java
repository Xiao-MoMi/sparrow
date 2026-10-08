package net.momirealms.sparrow.feature.spawn;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.feature.spawn.command.DelSpawnCommand;
import net.momirealms.sparrow.feature.spawn.command.SetSpawnCommand;
import net.momirealms.sparrow.feature.spawn.command.SpawnCommand;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.configuration.ServerConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class SpawnFeature extends Feature<SpawnSettings> {
    public static final String ID = "spawn";

    private final SparrowPlugin plugin;
    private SpawnService service;
    private volatile @Nullable Spawn spawn;

    public SpawnFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        this.config = this.plugin.configurationManager().featuresConfig().config().spawn();
    }

    @Override
    protected void onLoad() {
        this.service = new SpawnService();
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        CommandManager manager = this.plugin.commandManager();
        register.accept(new SpawnCommand(manager, this.plugin, this));
        register.accept(new SetSpawnCommand(manager, this.plugin, this));
        register.accept(new DelSpawnCommand(manager, this.plugin, this));
    }

    @Override
    protected void onEnable() {
        this.service.load();
        SpawnMessage.listener(this::accept);
    }

    @Override
    protected void onDisable() {
        SpawnMessage.listener(null);
        this.spawn = null;
    }

    private void accept(@NotNull SpawnMessage message) {
        if (!message.origin().equals(ServerConfig.serverId())) {
            this.setSpawn(message.spawn());
        }
    }

    void setSpawn(@Nullable Spawn spawn) {
        boolean visibilityChanged = (this.spawn == null) != (spawn == null);
        this.spawn = spawn;
        if (visibilityChanged) {
            this.plugin.featureManager().refreshCommands();
        }
    }

    @Nullable
    public Spawn spawn() {
        return this.spawn;
    }

    @NotNull
    public SpawnService service() {
        return this.service;
    }
}