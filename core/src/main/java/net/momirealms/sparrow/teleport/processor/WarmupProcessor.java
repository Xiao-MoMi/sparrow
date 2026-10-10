package net.momirealms.sparrow.teleport.processor;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.minecraft.world.BossEvent;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.teleport.Teleport;
import net.momirealms.sparrow.teleport.TeleportProcessor;
import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Configuration(naming = Configuration.Naming.KEBAB_CASE)
public final class WarmupProcessor implements TeleportProcessor.Pre {
    public static final String SECONDS_NODE = DependencyVersions.PROJECT_ID + ".teleport-warmup"; // 后接秒数, 覆盖配置的预热时间

    private int seconds = 3;
    private String bypassPermission = DependencyVersions.PROJECT_ID + ".bypass.teleport-warmup";
    private boolean cancelOnMove = true;
    private boolean cancelOnDamage = true;
    private WarmupDisplay display = WarmupDisplay.ACTION_BAR;
    private BossEvent.BossBarColor bossBarColor = BossEvent.BossBarColor.YELLOW;
    private BossEvent.BossBarOverlay bossBarOverlay = BossEvent.BossBarOverlay.PROGRESS;
    private Sound warmupSound = Sound.sound(Key.key("block.note_block.banjo"), Sound.Source.MASTER, 1.0f, 1.0f);
    private Sound cancelSound = Sound.sound(Key.key("entity.item.break"), Sound.Source.MASTER, 1.0f, 1.0f);

    @NotNull
    @Override
    public CompletableFuture<Component> before(@NotNull BukkitSparrowPlayer player, @NotNull Teleport teleport) {
        if (!teleport.self()) return PASS;
        if (!this.bypassPermission.isEmpty() && player.hasPermission(this.bypassPermission)) return PASS;
        // 预热取配置值与 sparrow.teleport-warmup.<秒> 中最小的节点
        int seconds = SparrowPlugin.instance().compatibilityManager().permissionMinimum(player.platformPlayer(), SECONDS_NODE, this.seconds);
        if (seconds <= 0) return PASS;
        return SparrowPlugin.instance().teleportService().warmupManager().start(player, this, seconds);
    }

    public boolean cancelOnMove() {
        return this.cancelOnMove;
    }

    public boolean cancelOnDamage() {
        return this.cancelOnDamage;
    }

    @NotNull
    public WarmupDisplay display() {
        return this.display;
    }

    @NotNull
    public BossEvent.BossBarColor bossBarColor() {
        return this.bossBarColor;
    }

    @NotNull
    public BossEvent.BossBarOverlay bossBarOverlay() {
        return this.bossBarOverlay;
    }

    @NotNull
    public Sound warmupSound() {
        return this.warmupSound;
    }

    @NotNull
    public Sound cancelSound() {
        return this.cancelSound;
    }
}