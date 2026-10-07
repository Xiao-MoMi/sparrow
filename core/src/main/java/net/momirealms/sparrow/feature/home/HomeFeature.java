package net.momirealms.sparrow.feature.home;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.feature.home.command.*;
import net.momirealms.sparrow.feature.home.placehoder.HomesCountPlaceholder;
import net.momirealms.sparrow.feature.home.placehoder.HomesListPlaceholder;
import net.momirealms.sparrow.feature.home.placehoder.MaxHomesPlaceholder;
import net.momirealms.sparrow.player.PlayerListener;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.suggestion.Suggestion;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class HomeFeature extends Feature<HomeSettings> implements PlayerListener {
    public static final String ID = "home";

    private final SparrowPlugin plugin;
    private volatile HomeService service;
    private final List<PlaceholderExpansion> placeholders = new ArrayList<>(3);
    private final Cache<String, CompletableFuture<HomeSnapshot>> suggestions = Caffeine.newBuilder().maximumSize(128).expireAfterWrite(Duration.ofSeconds(5)).build();

    public HomeFeature(@NotNull SparrowPlugin plugin) {
        super(ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        HomeSettings settings = this.plugin.configurationManager().featuresConfig().config().home();
        settings.validate();
        this.config = settings;
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        register.accept(new HomeCommand(this));
        register.accept(new SetHomeCommand(this));
        register.accept(new DelHomeCommand(this));
        register.accept(new DelAllHomeCommand(this));
        register.accept(new HomeListCommand(this));
        register.accept(new EditHomeCommand(this));
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
        if (this.plugin.compatibilityManager().hasPlaceholderAPI()) {
            PlaceholderExpansion[] placeholders = {new HomesCountPlaceholder(), new MaxHomesPlaceholder(), new HomesListPlaceholder()};
            for (int i = 0; i < placeholders.length; i++) {
                PlaceholderExpansion placeholder = placeholders[i];
                if (placeholder.register()) {
                    this.placeholders.add(placeholder);
                }
            }
        }
    }

    public int limit(@NotNull Player player) {
        return this.plugin.compatibilityManager().permissionLimit(player, "sparrow.max-homes", this.config.maxHomes());
    }

    @NotNull
    public String permission(String command) {
        return this.plugin.configurationManager().commandsConfig().configDefinition().command(command).getPermission();
    }

    @NotNull
    public String usage(String command) {
        return this.plugin.configurationManager().commandsConfig().configDefinition().command(command).getUsages().stream()
                .filter(usage -> usage.startsWith("/")).findFirst().orElse("/" + command);
    }

    @NotNull
    public CompletableFuture<List<Suggestion>> suggest(CommandSender sender, String input, boolean qualified, String permission) {
        HomeService service = this.service;
        if (service == null) return CompletableFuture.completedFuture(List.of());
        int separator = qualified ? input.lastIndexOf('.') : -1;
        if (separator < 0) {
            List<String> names = sender instanceof Player player ? service.complete(player.getUniqueId(), input, this.config.suggestionLimit()) : List.of();
            return CompletableFuture.completedFuture(names.stream().map(Suggestion::suggestion).toList());
        }
        String owner = input.substring(0, separator);
        String prefix = input.substring(separator + 1);
        if (sender instanceof Player player && player.getName().equalsIgnoreCase(owner)) {
            return CompletableFuture.completedFuture(service.complete(player.getUniqueId(), prefix, this.config.suggestionLimit()).stream()
                    .map(name -> Suggestion.suggestion(owner + "." + name)).toList());
        }
        if (!sender.hasPermission(permission + ".other")) return CompletableFuture.completedFuture(List.of());
        // 他人补全短暂保留查询结果, 让同步补全在下次按 Tab 时能取得异步结果.
        CompletableFuture<HomeSnapshot> loading = this.suggestions.get(owner, name -> this.plugin.playerManager().resolvePlayer(name)
                .thenCompose(found -> found.isPresent() ? service.snapshot(found.get().uuid()) : CompletableFuture.completedFuture(new HomeSnapshot(List.of()))));
        CompletableFuture<List<Suggestion>> result = loading.thenApply(snapshot -> snapshot.complete(prefix, this.config.suggestionLimit())
                .stream()
                .map(name -> Suggestion.suggestion(owner + "." + name)).toList());
        return this.plugin.commandManager().asynchronousCompletion() ? result : CompletableFuture.completedFuture(result.getNow(List.of()));
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
        int size = this.placeholders.size();
        for (int i = 0; i < size; i++) {
            this.placeholders.get(i).unregister();
        }
        this.placeholders.clear();
        HomeChangedMessage.listener(null);
        this.suggestions.invalidateAll();
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
