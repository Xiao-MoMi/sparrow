package net.momirealms.sparrow.teleport.processor;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class TeleportWarmup {
    private static final double MOVE_TOLERANCE_SQUARED = 0.25; // 允许 0.5 格以内的晃动

    private final WarmupManager manager;
    private final BukkitSparrowPlayer player;
    private final WarmupProcessor options;
    private final CompletableFuture<Component> result; // 结果的含义与出发前处理器相同
    private final Location start;
    private final UUID bossBarId = UUID.randomUUID();
    private final Sound warmupSound;
    private final Sound cancelSound;
    private final int warmupTicks;
    private int ticksLeft;
    private SchedulerTask task;

    TeleportWarmup(
            @NotNull WarmupManager manager,
            @NotNull BukkitSparrowPlayer player,
            @NotNull WarmupProcessor options,
            int seconds,
            @NotNull CompletableFuture<Component> result
    ) {
        this.manager = manager;
        this.player = player;
        this.options = options;
        this.result = result;
        this.start = player.platformPlayer().getLocation();
        this.warmupSound = options.warmupSound();
        this.cancelSound = options.cancelSound();
        this.warmupTicks = seconds * 20;
        this.ticksLeft = this.warmupTicks;
    }

    void start() {
        if (this.result.isDone()) {
            return;
        }
        this.countdown();
        this.task = SparrowPlugin.instance().scheduler().platform().runRepeating(this::tick, () -> this.cancel(null), 1, 1, this.player.platformPlayer());
        // 关服时可能在任务创建前就从其他线程取消了
        if (this.result.isDone()) {
            this.task.cancel();
            this.hideBossBar();
        }
    }

    boolean cancelOnDamage() {
        return this.options.cancelOnDamage();
    }

    private void tick() {
        if (this.options.cancelOnMove() && this.moved()) {
            this.cancel(MessageConstants.TELEPORT_CANCELLED_MOVED);
            return;
        }
        this.ticksLeft--;
        if (this.ticksLeft <= 0) {
            this.stop(null);
            return;
        }
        if (this.options.display() == WarmupDisplay.BOSS_BAR) {
            this.player.updateBossBarProgress(this.bossBarId, (float) this.ticksLeft / this.warmupTicks);
        }
        // 每过一秒刷新一次倒计时
        if (this.ticksLeft % 20 == 0) {
            this.countdown();
        }
    }

    // 按配置的位置显示剩余秒数并播放预热音效
    private void countdown() {
        Component seconds = Component.text(this.ticksLeft / 20);
        switch (this.options.display()) {
            case ACTION_BAR -> this.player.sendActionBar(MessageConstants.TELEPORT_WARMUP, seconds);
            case TITLE -> this.player.sendTitle(
                    Component.empty(),
                    this.player.render(MessageConstants.TELEPORT_WARMUP.arguments(seconds)),
                    0,
                    25,
                    5
            );
            case BOSS_BAR -> {
                Component title = this.player.render(MessageConstants.TELEPORT_WARMUP.arguments(seconds));
                if (this.ticksLeft == this.warmupTicks) {
                    this.player.showBossBar(this.bossBarId, title, 1.0f, this.options.bossBarColor(), this.options.bossBarOverlay());
                } else {
                    this.player.updateBossBarTitle(this.bossBarId, title);
                }
            }
            case CHAT -> this.player.sendMessage(MessageConstants.TELEPORT_WARMUP, seconds);
            case NONE -> {
            }
        }
        this.player.playSound(this.warmupSound);
    }

    private boolean moved() {
        Location now = this.player.platformPlayer().getLocation();
        return now.getWorld() != this.start.getWorld() || now.distanceSquared(this.start) > MOVE_TOLERANCE_SQUARED;
    }

    // reason 为 null 时静默取消, 例如被新的传送替换、玩家离开或插件关闭
    void cancel(@Nullable TranslatableComponent reason) {
        if (!this.stop(reason == null ? Component.empty() : reason) || reason == null) {
            return;
        }
        this.player.playSound(this.cancelSound);
    }

    // 多个线程同时结束时只有一方生效, 返回是否由本次结束. denial 为 null 表示倒计时走完
    private boolean stop(@Nullable Component denial) {
        if (this.task != null) {
            this.task.cancel();
        }
        this.manager.finished(this.player.uniqueId(), this);
        if (this.result.isDone()) {
            return false;
        }
        this.hideBossBar();
        // 先替换掉倒计时再交出结果, 避免传送后客户端还显示着剩余秒数
        if (denial == null) {
            // 走完时在倒计时的位置显示正在传送;
            switch (this.options.display()) {
                case ACTION_BAR -> this.player.sendActionBar(MessageConstants.TELEPORT_PROCESSING);
                case TITLE -> this.player.sendTitle(Component.empty(), this.player.render(MessageConstants.TELEPORT_PROCESSING), 0, 20, 5);
                case BOSS_BAR, CHAT, NONE -> {
                }
            }
        } else {
            this.clearCountdown();
        }
        return this.result.complete(denial);
    }

    private void hideBossBar() {
        if (this.options.display() == WarmupDisplay.BOSS_BAR) {
            this.player.hideBossBar(this.bossBarId);
        }
    }

    // 取消时主动清除动作栏和标题
    private void clearCountdown() {
        switch (this.options.display()) {
            case ACTION_BAR -> this.player.sendActionBar(Component.empty());
            case TITLE -> this.player.clearTitle();
            case BOSS_BAR, CHAT, NONE -> {
            }
        }
    }
}