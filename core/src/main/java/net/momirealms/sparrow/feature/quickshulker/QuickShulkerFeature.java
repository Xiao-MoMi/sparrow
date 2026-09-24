package net.momirealms.sparrow.feature.quickshulker;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.momirealms.sparrow.feature.Feature;
import net.momirealms.sparrow.plugin.configuration.FeaturesConfig;
import net.momirealms.sparrow.ui.SparrowUI;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
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
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player player = event.getPlayer();
        QuickShulkerSettings config = this.config();
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
        QuickShulkerMenu.open(player, sourceSlot, config.title(), this.state())
                .whenComplete((result, error) -> {
                    if (error != null) {
                        SparrowUI.getInstance().handleException("Failed to open quick shulker menu", error);
                    }
                });
    }
}
