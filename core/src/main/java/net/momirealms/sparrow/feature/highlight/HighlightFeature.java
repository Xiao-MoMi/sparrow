package net.momirealms.sparrow.feature.highlight;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.PlayerConnection;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.FeaturePermission;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import net.momirealms.sparrow.ui.Subscription;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.bukkit.Location;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class HighlightFeature implements Feature, Listener {
    public static final String ID = "highlight";
    public static final FeaturePermission COMMAND_PERMISSION = new FeaturePermission(ID);

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final List<Display> displays = new ArrayList<>();
    private SchedulerTask cleanup;
    private Subscription quitSubscription;
    private volatile HighlightSettings config;
    private long generation;

    @Override
    @NotNull
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
    private HighlightSettings readConfig() {
        HighlightSettings settings;
        try {
            settings = YamlMapperFactory.builder()
                    .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                    .build()
                    .create(HighlightSettings.class, HighlightSettings::new)
                    .load(this.plugin.dataFolderPath().resolve("features/highlight.yml"))
                    .value();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load features/highlight.yml", exception);
        }
        if (NamedTextColor.NAMES.value(settings.defaultColor().toLowerCase(Locale.ROOT)) == null
                || settings.defaultDuration() < 0 || settings.defaultDuration() > 300
                || settings.maxBlocks() <= 0 || settings.selectionTimeout() <= 0) {
            throw new IllegalArgumentException("Invalid highlight color, duration, max-blocks or selection-timeout");
        }
        return settings;
    }

    @Override
    @NotNull
    public CompletableFuture<Void> onEnable() {
        this.start(this.readConfig());
        return CompletableFuture.completedFuture(null);
    }

    private synchronized void start(@NotNull HighlightSettings settings) {
        this.config = settings;
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this, this.plugin.javaPlugin());
        this.quitSubscription = this.plugin.playerManager().registerQuitListener(player -> {
            this.clear(player.platformPlayer, false);
            return CompletableFuture.completedFuture(null);
        });
        this.cleanup = this.plugin.scheduler().asyncRepeating(this::expire, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    @NotNull
    public synchronized CompletableFuture<Void> onReload() {
        HighlightSettings settings = this.readConfig();
        this.onDisable();
        this.start(settings);
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    public HighlightSettings config() {
        return this.config;
    }

    private boolean enabled() {
        return this.plugin.featureManager().isEnabled(ID);
    }

    @Override
    @NotNull
    public synchronized CompletableFuture<Void> onDisable() {
        this.generation++;
        HandlerList.unregisterAll(this);
        if (this.quitSubscription != null) {
            this.quitSubscription.close();
            this.quitSubscription = null;
        }
        if (this.cleanup != null) {
            this.cleanup.cancel();
            this.cleanup = null;
        }
        this.selections.clear();
        for (int i = 0; i < this.displays.size(); i++) {
            Display display = this.displays.get(i);
            display.connection.sendPacket(display.removal);
        }
        this.displays.clear();
        return CompletableFuture.completedFuture(null);
    }

    public synchronized boolean cancelSelection(@NotNull Player player) {
        return this.selections.remove(player.getUniqueId()) != null;
    }

    public synchronized void select(@NotNull Player player, @NotNull Options options, @NotNull Feedback feedback) {
        if (!this.enabled()) return;
        this.selections.put(player.getUniqueId(), new Selection(options, feedback, System.nanoTime() + TimeUnit.SECONDS.toNanos(this.config.selectionTimeout())));
        feedback.send(MessageConstants.COMMAND_HIGHLIGHT_TIP);
    }

    @EventHandler
    public synchronized void onInteract(@NotNull PlayerInteractEvent event) {
        if (!this.enabled() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Selection selection = this.selections.get(event.getPlayer().getUniqueId());
        if (selection == null) {
            return;
        }
        Location point;
        switch (event.getAction()) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> point = event.getPlayer().getLocation();
            case RIGHT_CLICK_BLOCK -> point = event.getClickedBlock().getLocation();
            default -> { return; }
        }
        event.setCancelled(true);
        if (selection.first == null) {
            selection.first = point;
            selection.feedback.send(
                    MessageConstants.COMMAND_HIGHLIGHT_FIRST,
                    Component.text(point.getBlockX()),
                    Component.text(point.getBlockY()),
                    Component.text(point.getBlockZ())
            );
        } else {
            this.selections.remove(event.getPlayer().getUniqueId());
            this.show(selection.first, point, selection.options, selection.feedback);
        }
    }

    /** 校验选区后读取各区块, 完成时给仍在选区世界内的目标玩家发送轮廓. */
    public synchronized void show(@NotNull Location first, @NotNull Location second, @NotNull Options options, @NotNull Feedback feedback) {
        if (!this.enabled()) {
            for (Player viewer : options.viewers) {
                feedback.unavailable(viewer.getName());
            }
            return;
        }
        World world = first.getWorld();
        if (world == null || world != second.getWorld()) {
            feedback.send(MessageConstants.COMMAND_HIGHLIGHT_WORLD);
            return;
        }
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            feedback.send(MessageConstants.COMMAND_HIGHLIGHT_PEACEFUL);
            return;
        }
        HighlightRegion region;
        try {
            region = HighlightRegion.between(first, second, this.config.maxBlocks());
        } catch (IllegalArgumentException exception) {
            feedback.send(MessageConstants.COMMAND_HIGHLIGHT_TOO_LARGE, Component.text(this.config.maxBlocks()));
            return;
        }
        if (region.minY() < world.getMinHeight() || (long) region.minY() + region.sizeY() > world.getMaxHeight()) {
            feedback.send(MessageConstants.COMMAND_HIGHLIGHT_HEIGHT);
            return;
        }
        long expectedGeneration = this.generation;
        boolean[] solid = options.solidOnly ? new boolean[region.volume()] : null;
        if (solid != null) {
            for (int x = 0; x < region.sizeX(); x++) {
                for (int z = 0; z < region.sizeZ(); z++) {
                    for (int y = 0; y < region.sizeY(); y++) {
                        solid[region.index(x, y, z)] = !world.getBlockAt(region.minX() + x, region.minY() + y, region.minZ() + z).isPassable();
                    }
                }
            }
        }
        CompletableFuture.runAsync(() -> {
            HighlightBlocks blocks = new HighlightBlocks(region, solid, options.color);
            for (int i = 0; i < options.viewers.size(); i++) {
                Player viewer = options.viewers.get(i);
                synchronized (this) {
                    if (!this.current(expectedGeneration)) {
                        feedback.unavailable(viewer.getName());
                        continue;
                    }
                    BukkitSparrowPlayer player = this.plugin.playerManager().getOnlinePlayer(viewer);
                    if (player == null || viewer.getWorld() != world) {
                        feedback.unavailable(viewer.getName());
                        continue;
                    }
                    blocks.show(player.connection);
                    if (options.duration == 0) {
                        blocks.destroy(player.connection);
                    } else {
                        this.displays.add(
                                new Display(
                                        player.connection,
                                        blocks.removalPacket(),
                                        System.nanoTime() + TimeUnit.SECONDS.toNanos(options.duration)
                                )
                        );
                    }
                    feedback.send(MessageConstants.COMMAND_HIGHLIGHT_SUCCESS, Component.text(viewer.getName()));
                }
            }
        }, this.plugin.scheduler().async()).whenComplete((ignored, error) -> {
            if (error != null) feedback.failed(error);
        });
    }

    private synchronized boolean current(long generation) {
        return this.enabled() && this.generation == generation;
    }

    private synchronized void expire() {
        long now = System.nanoTime();
        Iterator<Selection> selections = this.selections.values().iterator();
        while (selections.hasNext()) {
            Selection selection = selections.next();
            if (now >= selection.expires) {
                selections.remove();
                selection.feedback.send(MessageConstants.COMMAND_HIGHLIGHT_TIMEOUT);
            }
        }
        Iterator<Display> displays = this.displays.iterator();
        while (displays.hasNext()) {
            Display display = displays.next();
            if (now >= display.expires) {
                displays.remove();
                display.connection.sendPacket(display.removal);
            }
        }
    }

    @EventHandler
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
        this.clear(event.getPlayer(), true);
    }

    @EventHandler
    public void onRespawn(@NotNull PlayerRespawnEvent event) {
        this.clear(event.getPlayer(), true);
    }

    private synchronized void clear(Player player, boolean destroy) {
        UUID id = player.getUniqueId();
        this.selections.remove(id);
        Iterator<Display> displays = this.displays.iterator();
        while (displays.hasNext()) {
            Display display = displays.next();
            if (display.connection.uniqueId().equals(id)) {
                displays.remove();
                if (destroy) {
                    display.connection.sendPacket(display.removal);
                }
            }
        }
    }

    public record Options(@NotNull List<Player> viewers, @NotNull NamedTextColor color, int duration, boolean solidOnly) {
        public Options {
            viewers = List.copyOf(viewers);
        }
    }

    @FunctionalInterface
    public interface Feedback {

        void send(TranslatableComponent key, Component... arguments);

        default void unavailable(String name) {
        }

        default void failed(Throwable error) {
            SparrowPlugin.instance().logger().warn("Failed to show highlight", error);
        }
    }

    private static final class Selection {
        private final Options options;
        private final Feedback feedback;
        private final long expires;
        private Location first;

        private Selection(Options options, Feedback feedback, long expires) {
            this.options = options;
            this.feedback = feedback;
            this.expires = expires;
        }
    }

    private record Display(PlayerConnection connection, ClientboundBundlePacket removal, long expires) {
    }
}