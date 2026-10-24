package net.momirealms.sparrow.feature.playerlimit;

import io.papermc.paper.event.player.PlayerServerFullCheckEvent;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class PlayerLimitFeature implements Feature, Listener {
    public static final String ID = "player_limit";

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private int originalMaxPlayers;

    @NotNull
    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supportsHotEnable() {
        return true;
    }

    @Override
    public boolean supportsHotDisable() {
        return true;
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onEnable() {
        this.originalMaxPlayers = Bukkit.getMaxPlayers();
        this.applyLimit(this.readConfig().maxPlayers());
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this, this.plugin.javaPlugin());
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    private YamlMapper<PlayerLimitSettings> createMapper() {
        return YamlMapperFactory.builder()
                .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                .build()
                .create(PlayerLimitSettings.class, PlayerLimitSettings::new);
    }

    @NotNull
    private PlayerLimitSettings readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features/player_limit.yml");
        PlayerLimitSettings settings;
        try {
            settings = this.createMapper().load(path).value();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + path, exception);
        }
        if (settings.maxPlayers() < -1) throw new IllegalArgumentException("max-players must be at least -1");
        return settings;
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onReload() {
        this.applyLimit(this.readConfig().maxPlayers());
        return CompletableFuture.completedFuture(null);
    }

    public void setMaxPlayers(int maxPlayers) {
        PlayerLimitSettings settings = new PlayerLimitSettings();
        settings.maxPlayers(maxPlayers);
        Path path = this.plugin.dataFolderPath().resolve("features/player_limit.yml");
        try {
            this.createMapper().save(path, settings);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save " + path, exception);
        }
        this.applyLimit(maxPlayers);
    }

    private void applyLimit(int maxPlayers) {
        Bukkit.setMaxPlayers(maxPlayers == -1 ? this.originalMaxPlayers : maxPlayers);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFullCheck(@NotNull PlayerServerFullCheckEvent event) {
        if (event.isAllowed()) return;
        if (this.plugin.compatibilityManager().hasPermission(event.getPlayerProfile().getId(), DependencyVersions.PROJECT_ID + ".bypass.player_limit")) {
            event.allow(true);
        }
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.setMaxPlayers(this.originalMaxPlayers);
        return CompletableFuture.completedFuture(null);
    }
}