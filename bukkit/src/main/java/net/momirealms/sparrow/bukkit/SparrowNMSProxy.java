package net.momirealms.sparrow.bukkit;

import net.momirealms.sparrow.heart.SparrowHeart;
import net.momirealms.sparrow.heart.feature.inventory.HandSlot;
import org.bukkit.entity.Player;
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

    public void swingHand(@NotNull Player player, @NotNull HandSlot slot) {
        requireNonNull(player, "player");
        requireNonNull(slot, "slot");
        heart.swingHand(player, slot);
    }

}
