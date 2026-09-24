package net.momirealms.sparrow.feature.quickshulker;

import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.configuration.FeaturesConfig;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class QuickShulkerFeature extends Feature<QuickShulkerSettings> implements Listener {
    private final JavaPlugin plugin;
    private final FeaturesConfig featuresConfig;

    public QuickShulkerFeature(@NotNull JavaPlugin plugin, @NotNull FeaturesConfig featuresConfig) {
        super("quick-shulker");
        this.plugin = plugin;
        this.featuresConfig = featuresConfig;
    }

    @Override
    public void loadConfig() {
        this.config = this.featuresConfig.config().quickShulker();
    }

    @Override
    protected void onLoad() {
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
    }

    @EventHandler
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (!this.enabled()) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        QuickShulkerSettings config = this.config();
        if (config.requireSneaking() && !event.getPlayer().isSneaking()) return;
        if (!config.allowOffhand() && event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (config.disabledWorlds().contains(event.getPlayer().getWorld().getName())) return;
        // TODO 打开潜影盒快捷菜单
    }
}
