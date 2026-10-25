package net.momirealms.sparrow.service.countdown;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.momirealms.sparrow.service.countdown.CountdownCancelledException.Reason;
import net.momirealms.sparrow.player.BukkitSparrowPlayer;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.scheduler.executor.PlatformExecutor;
import net.momirealms.sparrow.plugin.scheduler.task.SchedulerTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class Countdown {
    private static final double MOVE_TOLERANCE_SQUARED = 0.25; // 允许 0.5 格以内的晃动

    final CompletableFuture<Void> result = new CompletableFuture<>();
    final CountdownSettings settings;
    private final SparrowPlugin plugin = SparrowPlugin.instance();
    private final CountdownService service;
    private final BukkitSparrowPlayer player;
    private final TranslatableComponent text;
    private final UUID bossBarId = UUID.randomUUID();
    private final int totalTicks;

    // 以下状态只在玩家线程上读写
    private int ticksLeft;
    private boolean started;
    private Level startLevel;
    private double startX;
    private double startY;
    private double startZ;
    private volatile @Nullable SchedulerTask task; // 结束可能发生在任何线程, 收尾时要能看到它

    Countdown(@NotNull CountdownService service, @NotNull BukkitSparrowPlayer player, @NotNull CountdownSettings settings, @NotNull TranslatableComponent text) {
        this.service = service;
        this.player = player;
        this.settings = settings;
        this.text = text;
        this.totalTicks = (int) Math.max(1, (settings.duration().toMillis() + 49) / 50);
        this.ticksLeft = this.totalTicks;
        this.result.whenComplete((ignored, error) -> this.finish(error));
    }

    // 在玩家线程上开始: 记下起点, 顶掉同一玩家之前的倒计时, 显示第一帧后每 tick 检查一次
    void start() {
        if (this.result.isDone()) return;
        ServerPlayer nmsPlayer = this.player.nmsPlayer;
        this.startLevel = nmsPlayer.level();
        this.startX = nmsPlayer.getX();
        this.startY = nmsPlayer.getY();
        this.startZ = nmsPlayer.getZ();
        Countdown previous = this.service.countdowns.put(this.player.uniqueId(), this);
        if (previous != null) previous.cancel(Reason.REPLACED);
        this.started = true;
        this.show();
        SchedulerTask task = this.plugin.scheduler().platform().runRepeating(this::tick, () -> this.cancel(Reason.QUIT), 1, 1, this.player.platformPlayer);
        if (task == null) {
            this.cancel(Reason.QUIT);
            return;
        }
        this.task = task;
    }

    void cancel(@NotNull Reason reason) {
        this.result.completeExceptionally(new CountdownCancelledException(reason));
    }

    private void tick() {
        // 已经结束时收尾任务马上就会执行, 这里不再发新的画面
        if (this.result.isDone()) return;
        if (this.settings.cancelOnMove() && this.moved()) {
            this.cancel(Reason.MOVED);
            return;
        }
        if (--this.ticksLeft <= 0) {
            this.result.complete(null);
            return;
        }
        if (this.settings.display() == CountdownDisplay.BOSS_BAR) {
            this.player.updateBossBarProgress(this.bossBarId, (float) this.ticksLeft / this.totalTicks);
        }
        if (this.ticksLeft % 20 == 0) {
            this.show();
        }
    }

    private boolean moved() {
        ServerPlayer nmsPlayer = this.player.nmsPlayer;
        return nmsPlayer.level() != this.startLevel || nmsPlayer.distanceToSqr(this.startX, this.startY, this.startZ) > MOVE_TOLERANCE_SQUARED;
    }

    // 按剩余秒数刷新一帧画面并播放每秒音效, 不足一秒按一秒显示
    private void show() {
        Component seconds = Component.text((this.ticksLeft + 19) / 20);
        switch (this.settings.display()) {
            case ACTION_BAR -> this.player.sendActionBar(this.text, seconds);
            case TITLE -> this.player.sendTranslatedTitle(Component.empty(), this.text.arguments(seconds), 0, 25, 5);
            case BOSS_BAR -> {
                Component title = this.plugin.translationManager().render(this.text.arguments(seconds), this.player.locale(), this.player.platformPlayer);
                if (this.ticksLeft == this.totalTicks) {
                    this.player.showBossBar(this.bossBarId, title, 1.0F, this.settings.bossBarColor(), this.settings.bossBarOverlay());
                } else {
                    this.player.updateBossBarTitle(this.bossBarId, title);
                }
            }
            case CHAT -> this.player.sendMessage(this.text, seconds);
            case NONE -> {
            }
        }
        this.player.playSound(this.settings.tickSound());
    }

    // 收尾放到玩家线程上, 和 tick 串行执行, 清掉画面之后不会再有新的画面发出
    private void finish(@Nullable Throwable error) {
        PlatformExecutor executor = this.plugin.scheduler().platform();
        if (executor.isOwnedByCurrentRegion(this.player.platformPlayer)) {
            this.cleanup(error);
        } else {
            executor.run(() -> this.cleanup(error), this::release, this.player.platformPlayer);
        }
    }

    private void cleanup(@Nullable Throwable error) {
        this.release();
        if (!this.started) return;
        switch (this.settings.display()) {
            case ACTION_BAR -> this.player.sendActionBar(Component.empty());
            case TITLE -> this.player.clearTitle();
            case BOSS_BAR -> this.player.hideBossBar(this.bossBarId);
            case CHAT, NONE -> {
            }
        }
        // 玩家自己打断时才播放取消音效
        if (error instanceof CountdownCancelledException cancelled && (cancelled.reason() == Reason.MOVED || cancelled.reason() == Reason.DAMAGED)) {
            this.player.playSound(this.settings.cancelSound());
        }
    }

    private void release() {
        SchedulerTask task = this.task;
        if (task != null) task.cancel();
        this.service.countdowns.remove(this.player.uniqueId(), this);
    }
}