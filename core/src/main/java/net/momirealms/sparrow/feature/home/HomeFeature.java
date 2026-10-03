package net.momirealms.sparrow.feature.home;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.jetbrains.annotations.NotNull;

public final class HomeFeature extends Feature<HomeSettings> implements PlayerListener {
    public static final String ID = "home";

    private final SparrowPlugin plugin;
    private volatile HomeService service;

    public HomeFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        HomeSettings settings = this.plugin.configurationManager().featuresConfig().config().home();
        if (settings.cacheTtlSeconds() < 1) {
            throw new IllegalArgumentException("home.cache-ttl-seconds must be positive");
        }
        this.config = settings;
    }

    @Override
    protected void onLoad() {
        this.plugin.playerManager().registerListener(this);
    }

    @Override
    protected void onEnable() {
        this.plugin.dataStorage().homeStore().initialize().join();
        HomeService service = new HomeService();
        this.service = service;
        HomeChangedMessage.listener(service::accept);
        for (SparrowPlayer player : this.plugin.playerManager().getOnlinePlayers()) {
            this.plugin.scheduler().platform().run(() -> {
                if (this.service == service) {
                    this.onJoin(player);
                }
            }, () -> {}, player.platformPlayer());
        }
    }

    @Override
    public void onJoin(@NotNull SparrowPlayer player) {
        HomeService service = this.service;
        if (service != null && this.plugin.playerManager().getPlayer(player.uniqueId()) == player) {
            service.join(player);
        }
    }

    @Override
    public void onQuit(@NotNull SparrowPlayer player) {
        HomeService service = this.service;
        if (service != null) {
            service.quit(player);
        }
    }

    @Override
    protected void onDisable() {
        HomeChangedMessage.listener(null);
        HomeService service = this.service;
        this.service = null;
        if (service != null) {
            service.close();
        }
    }

    @Override
    protected void onUnload() {
        this.plugin.playerManager().unregisterListener(this);
    }

    @NotNull
    public HomeService service() {
        HomeService service = this.service;
        if (service == null) {
            throw new IllegalStateException("Home feature is disabled");
        }
        return service;
    }
}
