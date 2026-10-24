package net.momirealms.sparrow.player;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.world.BossEvent;
import net.momirealms.sparrow.compatibility.CompatibilityManager.PermissionValueMode;
import net.momirealms.sparrow.player.message.proxy.ConnectResult;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SparrowPlayer {

    @NotNull
    UUID uniqueId();

    @NotNull
    String name();

    Locale locale();

    boolean hasPermission(@NotNull String permission);

    @NotNull
    CompletableFuture<Boolean> hasPermissionAsync(@NotNull String permission);

    int getPermissionValue(@NotNull String node, int defaultValue, @NotNull PermissionValueMode mode);

    @NotNull
    CompletableFuture<Integer> getPermissionValueAsync(@NotNull String node, int defaultValue, @NotNull PermissionValueMode mode);

    boolean sendMessage(@NotNull String message);

    boolean sendMessage(@NotNull String message, boolean overlay);

    boolean sendMessage(@NotNull Component message);

    boolean sendMessage(@NotNull Component message, boolean overlay);

    boolean sendMessage(@NotNull TranslatableComponent message);

    boolean sendMessage(@NotNull TranslatableComponent key, @NotNull Component... arguments);

    boolean sendActionBar(@NotNull String message);

    boolean sendActionBar(@NotNull Component message);

    boolean sendActionBar(@NotNull TranslatableComponent message);

    boolean sendActionBar(@NotNull TranslatableComponent key, @NotNull Component... arguments);

    boolean sendTitle(@NotNull String title, @NotNull String subtitle, int fadeIn, int stay, int fadeOut);

    boolean sendTitle(@NotNull Component title, @NotNull Component subtitle, int fadeIn, int stay, int fadeOut);

    boolean clearTitle();

    boolean showBossBar(@NotNull UUID id, @NotNull Component title, float progress, @NotNull BossEvent.BossBarColor color, @NotNull BossEvent.BossBarOverlay overlay);

    boolean updateBossBarProgress(@NotNull UUID id, float progress);

    boolean updateBossBarTitle(@NotNull UUID id, @NotNull Component title);

    boolean hideBossBar(@NotNull UUID id);

    @NotNull
    CompletableFuture<ConnectResult> connect(@NotNull String server);

    boolean kick(boolean disconnectFromProxy, @Nullable Component reason);

    boolean playSound(@NotNull Sound sound);

    boolean sendToast(@NotNull String text, @NotNull ItemStack icon, @NotNull AdvancementType frame);

    boolean sendToast(@NotNull Component text, @NotNull ItemStack icon, @NotNull AdvancementType frame);

    boolean sendTotemAnimation(@NotNull ItemStack totem);

    boolean isOnlineLocally();

    boolean isOnlineGlobally();

    @Nullable
    String getCurrentServerId();
}