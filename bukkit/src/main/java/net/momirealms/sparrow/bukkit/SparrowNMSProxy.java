package net.momirealms.sparrow.bukkit;

import net.momirealms.sparrow.heart.SparrowHeart;
import net.momirealms.sparrow.heart.feature.color.NamedTextColor;
import net.momirealms.sparrow.heart.feature.highlight.HighlightBlocks;
import net.momirealms.sparrow.heart.feature.inventory.HandSlot;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

public class SparrowNMSProxy {

    private final SparrowHeart heart;

    public SparrowNMSProxy() {
        this.heart = SparrowHeart.getInstance();
    }

    public static SparrowNMSProxy getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final SparrowNMSProxy INSTANCE = new SparrowNMSProxy();
    }

    public void openCustomInventory(@NotNull Player player, @NotNull Inventory inventory, @NotNull String titleTitle) {
        requireNonNull(player, "player");
        requireNonNull(inventory, "inventory");
        requireNonNull(titleTitle, "titleTitle");
        heart.openCustomInventory(player, inventory, titleTitle);
    }

    public void updateInventoryTitle(@NotNull Player player, @NotNull String titleTitle) {
        requireNonNull(player, "player");
        requireNonNull(titleTitle, "titleTitle");
        heart.updateInventoryTitle(player, titleTitle);
    }

    public void swingHand(@NotNull Player player, @NotNull HandSlot slot) {
        requireNonNull(player, "player");
        requireNonNull(slot, "slot");
        heart.swingHand(player, slot);
    }

    public HighlightBlocks highlightBlocks(Player player, NamedTextColor color, Location... locations) {
        requireNonNull(player, "player");
        requireNonNull(color, "color");
        requireNonNull(locations, "locations");
        return heart.highlightBlocks(player, color, locations);
    }

    public void sendDebugMarker(Player player, Location location, String message, int duration, int color) {
        requireNonNull(player, "player");
        requireNonNull(message, "message");
        requireNonNull(location, "location");
        heart.sendDebugMarker(player, location, message, duration, color);
    }
}
