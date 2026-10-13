package net.momirealms.sparrow.feature.tpa;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.feature.tpa.command.TpaCancelCommand;
import net.momirealms.sparrow.feature.tpa.command.TpaCommand;
import net.momirealms.sparrow.feature.tpa.command.TpaReplyCommand;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public final class TpaFeature extends Feature<TpaSettings> {
    public static final String ID = "tpa";
    public static final String BYPASS_COOLDOWN_PERMISSION = DependencyVersions.PROJECT_ID + ".bypass.tpa-cooldown"; // 拥有此权限的玩家发起请求不受两种冷却限制

    private final SparrowPlugin plugin;
    private TpaService service;

    public TpaFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        TpaSettings settings = this.plugin.configurationManager().featuresConfig().config().tpa();
        if (settings.expireSeconds() < 1) throw new IllegalArgumentException("tpa.expire-seconds must be at least 1");
        super.config = settings;
    }

    @Override
    protected void onLoad() {
        this.service = new TpaService();
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        register.accept(new TpaCommand(this, "tpa", TpaRequest.Direction.TO_TARGET));
        register.accept(new TpaCommand(this, "tpa-here", TpaRequest.Direction.TO_REQUESTER));
        register.accept(new TpaReplyCommand(this, "tpa-accept", true));
        register.accept(new TpaReplyCommand(this, "tpa-deny", false));
        register.accept(new TpaCancelCommand(this));
    }

    void accept(@NotNull TpaMessage message) {
        if (this.enabled()) this.service.handle(message);
    }

    @NotNull
    public TpaService service() {
        return this.service;
    }
}