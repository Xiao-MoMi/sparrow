package net.momirealms.sparrow.feature.tpa;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.configuration.TeleportConfig;
import org.jetbrains.annotations.NotNull;

public final class TpaFeature extends Feature<TpaSettings> {
    public static final String ID = "tpa";

    private final SparrowPlugin plugin;

    public TpaFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        TpaSettings settings = this.plugin.configurationManager().featuresConfig().config().tpa();
        if (settings.expireSeconds() < 1) throw new IllegalArgumentException("tpa.expire-seconds must be at least 1");
        TeleportConfig.group(settings.teleportGroup());
        super.config = settings;
    }
}