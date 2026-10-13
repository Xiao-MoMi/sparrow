package net.momirealms.sparrow.feature.rtp;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public final class RtpFeature extends Feature<RtpSettings> {
    public static final String ID = "rtp";

    private final SparrowPlugin plugin = SparrowPlugin.instance();

    public RtpFeature() {
        super(ID);
    }

    @Override
    public void loadConfig() {
        RtpSettings settings = this.plugin.configurationManager().featuresConfig().config().rtp();
        super.config = settings;
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        register.accept(new RtpCommand(this.plugin.commandManager(), this.plugin));
    }
}