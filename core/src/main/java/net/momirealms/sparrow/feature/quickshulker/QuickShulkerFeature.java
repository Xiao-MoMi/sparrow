package net.momirealms.sparrow.feature.quickshulker;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.ui.SparrowUI;
import net.momirealms.sparrow.ui.window.Window;
import net.momirealms.sparrow.yaml.mapper.YamlMapper;
import net.momirealms.sparrow.yaml.mapper.YamlMapperFactory;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class QuickShulkerFeature implements Feature, Listener {
    public static final String ID = "quick-shulker";

    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final Set<Window> windows = new HashSet<>();
    private QuickShulkerSettings config;
    private boolean accepting;

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
        this.start(this.readConfig());
        return CompletableFuture.completedFuture(null);
    }

    @NotNull
    private QuickShulkerSettings readConfig() {
        Path path = this.plugin.dataFolderPath().resolve("features").resolve(ID + ".yml");
        YamlMapper<QuickShulkerSettings> mapper = YamlMapperFactory.builder()
                .sparrowYaml(this.plugin.configurationManager().sparrowYaml())
                .build()
                .create(QuickShulkerSettings.class, QuickShulkerSettings::new);
        try {
            return mapper.load(path).value();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load " + path, exception);
        }
    }

    private synchronized void start(@NotNull QuickShulkerSettings config) {
        this.config = config;
        this.plugin.javaPlugin().getServer().getPluginManager().registerEvents(this, this.plugin.javaPlugin());
        this.accepting = true;
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onReload() {
        QuickShulkerSettings loaded = this.readConfig();
        return this.onDisable().thenRun(() -> this.start(loaded));
    }

    @EventHandler
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        this.openMenu(event);
    }

    private synchronized void openMenu(@NotNull PlayerInteractEvent event) {
        if (!this.accepting) return;
        Player player = event.getPlayer();
        QuickShulkerSettings config = this.config;
        if (config.requireSneaking() && !player.isSneaking()) return;
        EquipmentSlot hand = event.getHand();
        if (!config.allowOffhand() && hand == EquipmentSlot.OFF_HAND) return;
        if (config.disabledWorlds().contains(player.getWorld().getName())) return;

        Inventory playerInventory = ((CraftPlayer) player).getHandle().getInventory();
        int selectedSlot = player.getInventory().getHeldItemSlot();
        int sourceSlot = hand == EquipmentSlot.HAND ? selectedSlot : Inventory.SLOT_OFFHAND;
        ItemStack shulker = playerInventory.getItem(sourceSlot);
        if (!shulker.is(ItemTags.SHULKER_BOXES)) return;
        event.setCancelled(true);

        // 双手同时持有潜影盒时只处理主手事件.
        if (hand == EquipmentSlot.OFF_HAND && playerInventory.getItem(selectedSlot).is(ItemTags.SHULKER_BOXES)) return;
        Window window = new QuickShulkerMenu(sourceSlot, config.title()).build(player);
        window.addCloseHandler(reason -> this.removeWindow(window));
        // 登记和提交打开共用停用时的锁, 关闭命令会排在已提交的打开命令之后.
        this.windows.add(window);
        window.open().whenComplete((result, error) -> {
            if (error != null || result == Window.OpenResult.VIEWER_UNAVAILABLE) {
                this.removeWindow(window);
            }
            if (error != null) {
                SparrowUI.getInstance().handleException("Failed to open quick shulker menu", error);
            }
        });
    }

    @NotNull
    @Override
    public CompletableFuture<Void> onDisable() {
        Window[] closing;
        synchronized (this) {
            this.accepting = false;
            HandlerList.unregisterAll(this);
            closing = this.windows.toArray(Window[]::new);
            this.windows.clear();
        }
        CompletableFuture<?>[] futures = new CompletableFuture<?>[closing.length];
        for (int i = 0; i < closing.length; i++) {
            futures[i] = closing[i].close();
        }
        return CompletableFuture.allOf(futures);
    }

    private synchronized void removeWindow(@NotNull Window window) {
        this.windows.remove(window);
    }
}