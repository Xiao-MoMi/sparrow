package net.momirealms.sparrow.player;

import net.kyori.adventure.text.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.momirealms.sparrow.advancement.AdvancementFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

public interface SparrowPlayer {

    @NotNull
    UUID uniqueId();

    @NotNull
    String name();

    @NotNull
    ServerPlayer nmsPlayer();

    @NotNull
    Player platformPlayer();

    @NotNull
    PlayerConnection connection();

    @NotNull
    Locale locale();

    boolean hasPermission(@NotNull String permission);

    @NotNull
    net.minecraft.world.item.ItemStack getItemInMainHand();

    void kick(@NotNull Component reason);

    void kickFromServer(@NotNull Component reason);

    void dropItem(@NotNull ItemStack stack);

    default void sendMessage(@NotNull Component message) {
        this.sendMessage(message, false);
    }

    void sendMessage(@NotNull Component message, boolean overlay);

    default void sendActionBar(@NotNull Component message) {
        this.sendMessage(message, true);
    }

    void sendTitle(@NotNull Component title, @NotNull Component subtitle, int fadeIn, int stay, int fadeOut);

    void showBossBar(@NotNull UUID id, @NotNull Component title, float progress, @NotNull BossEvent.BossBarColor color, @NotNull BossEvent.BossBarOverlay overlay);

    void hideBossBar(@NotNull UUID id);

    void sendTotemAnimation(@NotNull ItemStack totem);

    void sendToast(@NotNull Component text, @NotNull ItemStack icon, @NotNull AdvancementFrame frame);

    void sendDemo();

    void sendCredits();

    void sendDebugMarker(int x, int y, int z);
}