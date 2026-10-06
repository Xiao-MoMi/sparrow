package net.momirealms.sparrow.feature.highlight;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.PlayerConnection;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandFeature;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import org.bukkit.Location;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class HighlightFeature extends Feature<HighlightSettings> implements Listener {
    public static final String FEATURE_ID = "highlight";

    private final SparrowPlugin plugin;
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final List<Display> displays = new ArrayList<>();
    private SchedulerTask cleanup;
    private long generation;

    public HighlightFeature(@NotNull SparrowPlugin plugin) {
        super(FEATURE_ID);
        this.plugin = plugin;
    }

    @Override
    public void loadConfig() {
        HighlightSettings settings = this.plugin.configurationManager().featuresConfig().config().highlight();
        if (NamedTextColor.NAMES.value(settings.defaultColor().toLowerCase(Locale.ROOT)) == null
                || settings.defaultDuration() < 0 || settings.defaultDuration() > 300
                || settings.maxBlocks() <= 0 || settings.selectionTimeout() <= 0) {
            throw new IllegalArgumentException("Invalid highlight color, duration, max-blocks or selection-timeout");
        }
        this.config = settings;
    }

    @Override
    protected void onLoad() {
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this, this.plugin.javaPlugin());
    }

    @Override
    protected void registerCommand(@NotNull Consumer<CommandFeature> register) {
        register.accept(new HighlightCommand(this.plugin.commandManager(), this.plugin));
    }

    @Override
    protected synchronized void onEnable() {
        this.cleanup = this.plugin.scheduler().asyncRepeating(this::expire, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    protected synchronized void onDisable() {
        this.generation++;
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
    }

    @Override
    protected void onUnload() {
        HandlerList.unregisterAll(this);
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
        if (!this.enabled() || event.getHand() != EquipmentSlot.HAND) return;
        Selection selection = this.selections.get(event.getPlayer().getUniqueId());
        if (selection == null) return;
        Location point;
        switch (event.getAction()) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> point = event.getPlayer().getLocation();
            case RIGHT_CLICK_BLOCK -> point = event.getClickedBlock().getLocation();
            default -> { return; }
        }
        event.setCancelled(true);
        if (selection.first == null) {
            selection.first = point;
            selection.feedback.send(MessageConstants.COMMAND_HIGHLIGHT_FIRST, Component.text(point.getBlockX()), Component.text(point.getBlockY()), Component.text(point.getBlockZ()));
        } else {
            this.selections.remove(event.getPlayer().getUniqueId());
            this.show(selection.first, point, selection.options, selection.feedback);
        }
    }

    /** 校验选区后读取各区块, 完成时给仍在选区世界内的目标玩家发送轮廓. */
    public synchronized void show(@NotNull Location first, @NotNull Location second, @NotNull Options options, @NotNull Feedback feedback) {
        if (!this.enabled()) return;
        World world = first.getWorld();
        HighlightRegion region = this.validateRegion(first, second, world, feedback);
        if (region == null) return;
        long expectedGeneration = this.generation;
        boolean[] solid = options.solidOnly ? new boolean[region.volume()] : null;
        List<CompletableFuture<Void>> reads = this.readSolidBlocks(world, region, solid, expectedGeneration);
        CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new))
                .thenRunAsync(() -> this.showToViewers(world, region, solid, options, feedback, expectedGeneration), this.plugin.scheduler().async())
                .exceptionally(error -> {
                    this.plugin.logger().warn("Failed to create highlight", error);
                    if (this.current(expectedGeneration)) {
                        feedback.send(MessageConstants.COMMAND_HIGHLIGHT_FAILURE);
                    }
                    return null;
                });
    }

    private HighlightRegion validateRegion(Location first, Location second, @Nullable World world, Feedback feedback) {
        if (world == null || world != second.getWorld()) {
            feedback.send(MessageConstants.COMMAND_HIGHLIGHT_WORLD);
            return null;
        }
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            feedback.send(MessageConstants.COMMAND_HIGHLIGHT_PEACEFUL);
            return null;
        }
        HighlightRegion region;
        try {
            region = HighlightRegion.between(first, second, this.config.maxBlocks());
        } catch (IllegalArgumentException exception) {
            feedback.send(MessageConstants.COMMAND_HIGHLIGHT_TOO_LARGE, Component.text(this.config.maxBlocks()));
            return null;
        }
        if (region.minY() < world.getMinHeight() || (long) region.minY() + region.sizeY() > world.getMaxHeight()) {
            feedback.send(MessageConstants.COMMAND_HIGHLIGHT_HEIGHT);
            return null;
        }
        return region;
    }

    private List<CompletableFuture<Void>> readSolidBlocks(World world, HighlightRegion region, boolean[] solid, long expectedGeneration) {
        if (solid == null) return List.of();
        int maxX = region.minX() + region.sizeX() - 1;
        int maxZ = region.minZ() + region.sizeZ() - 1;
        List<CompletableFuture<Void>> reads = new ArrayList<>();
        // 每个任务只读取自己区块中的方块. 邻接判断在全部读取完成后使用数组进行.
        for (int chunkX = region.minX() >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = region.minZ() >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                int cx = chunkX;
                int cz = chunkZ;
                reads.add(CompletableFuture.runAsync(() -> this.readChunk(world, region, solid, expectedGeneration, maxX, maxZ, cx, cz),
                        task -> this.plugin.scheduler().platform().run(task, world, cx, cz)));
            }
        }
        return reads;
    }

    private void readChunk(World world, HighlightRegion region, boolean[] solid, long expectedGeneration, int maxX, int maxZ, int chunkX, int chunkZ) {
        if (!this.current(expectedGeneration)) return;
        int endX = Math.min(maxX, (chunkX << 4) + 15) - region.minX();
        int endZ = Math.min(maxZ, (chunkZ << 4) + 15) - region.minZ();
        for (int x = Math.max(region.minX(), chunkX << 4) - region.minX(); x <= endX; x++) {
            for (int z = Math.max(region.minZ(), chunkZ << 4) - region.minZ(); z <= endZ; z++) {
                for (int y = 0; y < region.sizeY(); y++) {
                    solid[region.index(x, y, z)] = !world.getBlockAt(region.minX() + x, region.minY() + y, region.minZ() + z).isPassable();
                }
            }
        }
    }

    private void showToViewers(World world, HighlightRegion region, boolean[] solid, Options options, Feedback feedback, long expectedGeneration) {
        if (!this.current(expectedGeneration)) return;
        HighlightBlocks blocks = new HighlightBlocks(region, solid, options.color);
        for (int i = 0; i < options.viewers.size(); i++) {
            Player viewer = options.viewers.get(i);
            synchronized (this) {
                if (!this.current(expectedGeneration)) return;
                SparrowPlayer player = this.plugin.playerManager().getPlayer(viewer);
                if (player == null || viewer.getWorld() != world) {
                    continue;
                }
                blocks.show(player.connection());
                if (options.duration == 0) {
                    blocks.destroy(player.connection());
                } else {
                    this.displays.add(new Display(player.connection(), blocks.removalPacket(), System.nanoTime() + TimeUnit.SECONDS.toNanos(options.duration)));
                }
                feedback.send(MessageConstants.COMMAND_HIGHLIGHT_SUCCESS, Component.text(viewer.getName()));
            }
        }
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
    public void onQuit(@NotNull PlayerQuitEvent event) {
        this.clear(event.getPlayer(), false);
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
        void send(TranslatableComponent.Builder key, Component... arguments);
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
