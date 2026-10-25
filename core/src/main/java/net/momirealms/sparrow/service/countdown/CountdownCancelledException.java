package net.momirealms.sparrow.service.countdown;

import org.jetbrains.annotations.NotNull;

public final class CountdownCancelledException extends RuntimeException {
    private final Reason reason;

    public CountdownCancelledException(@NotNull Reason reason) {
        // 倒计时被打断是正常的业务结果, 不需要栈信息
        super("Countdown cancelled: " + reason, null, false, false);
        this.reason = reason;
    }

    @NotNull
    public Reason reason() {
        return this.reason;
    }

    public enum Reason {
        MOVED,    // 玩家离开了起点
        DAMAGED,  // 玩家受到伤害
        REPLACED, // 同一玩家开始了新的倒计时
        QUIT      // 玩家离开了本服
    }
}