package net.momirealms.sparrow.player.teleport;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.player.SparrowPlayer;
import net.momirealms.sparrow.plugin.configuration.PluginConfig;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

// todo 优化
final class TeleportWarmup {
    private static final double MOVE_TOLERANCE_SQUARED = 0.25; // 允许 0.5 格以内的晃动

    private final TeleportService service;
    private final SparrowPlayer player;
    private final TeleportOptions options;
    private final CompletableFuture<Boolean> result;
    private final Location start;
    private int ticksLeft;
    private SchedulerTask task;

    TeleportWarmup(@NotNull TeleportService service, @NotNull SparrowPlayer player, @NotNull TeleportOptions options, @NotNull CompletableFuture<Boolean> result) {
        this.service = service;
        this.player = player;
        this.options = options;
        this.result = result;
        this.start = player.platformPlayer().getLocation();
        this.ticksLeft = options.warmupSeconds() * 20;
    }

    void start() {
        this.countdown();
        this.task = this.service.plugin().scheduler().platform().runRepeating(this::tick, () -> this.cancel(null), 1, 1, this.player.platformPlayer());
        // 关服时可能在任务创建前就从其他线程取消了
        if (this.result.isDone()) this.task.cancel();
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
            this.stop(true);
            return;
        }
        // 每过一秒刷新一次倒计时
        if (this.ticksLeft % 20 == 0) this.countdown();
    }

    // 按配置的位置显示剩余秒数并播放预热音效
    private void countdown() {
        Component seconds = Component.text(this.ticksLeft / 20);
        switch (PluginConfig.teleport().warmupDisplay()) {
            case ACTION_BAR -> this.player.sendActionBar(MessageConstants.TELEPORT_WARMUP, seconds);
            case TITLE -> this.player.sendTitle(Component.empty(), this.player.translate(MessageConstants.TELEPORT_WARMUP, seconds), 0, 25, 5);
            case CHAT -> this.player.sendMessage(MessageConstants.TELEPORT_WARMUP, seconds);
            case NONE -> {
            }
        }
        this.play(PluginConfig.teleport().warmupSound());
    }

    private void play(@Nullable Sound sound) {
        if (sound != null) this.player.playSound(sound);
    }

    private boolean moved() {
        Location now = this.player.platformPlayer().getLocation();
        return now.getWorld() != this.start.getWorld() || now.distanceSquared(this.start) > MOVE_TOLERANCE_SQUARED;
    }

    // reason 为 null 时静默取消, 例如被新的传送替换、玩家离开或插件关闭
    void cancel(@Nullable TranslatableComponent.Builder reason) {
        if (!this.stop(false) || reason == null) return;
        this.player.sendMessage(reason);
        this.play(PluginConfig.teleport().cancelSound());
    }

    // 多个线程同时结束时只有一方生效, 返回是否由本次结束
    private boolean stop(boolean completed) {
        if (this.task != null) this.task.cancel();
        this.service.finished(this.player.uniqueId(), this);
        return this.result.complete(completed);
    }
}
