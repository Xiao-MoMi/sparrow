package net.momirealms.sparrow.feature.bed;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.configuration.TeleportConfig;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public final class BedFeature extends Feature<BedSettings> {
    public static final String ID = "bed";

    private final SparrowPlugin plugin;

    public BedFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        BedSettings settings = this.plugin.configurationManager().featuresConfig().config().bed();
        TeleportConfig.group(settings.teleportGroup());
        super.config = settings;
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        register.accept(new BedCommand(this.plugin.commandManager(), this.plugin, this));
    }
}
