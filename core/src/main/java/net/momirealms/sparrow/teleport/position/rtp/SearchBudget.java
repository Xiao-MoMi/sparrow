package net.momirealms.sparrow.teleport.position.rtp;

import org.jetbrains.annotations.NotNull;

final class SearchBudget {
    private final int maxAttempts;   // 整轮允许开始的随机起点数
    private final int maxChecks;     // 整轮允许进入的列与扫描的 Y 层合计数
    private final long timeoutNanos; // 整轮时限, 来自已经校验的 RTP 配置
    private final long startedAt;    // 开始准备区域之前记录的单调时钟读数

    private int attempts;            // 已开始的随机起点数, 附近列不计入
    private int checks;              // 已取得执行额度的列和 Y 层数
    private volatile boolean stopped; // 搜索结果完成或取消后设置, 异步回调也能看见

    SearchBudget(@NotNull RTPPos options) {
        this.startedAt = System.nanoTime();
        this.maxAttempts = options.maxAttempts();
        this.maxChecks = options.maxChecks();
        this.timeoutNanos = options.timeout().toNanos();
    }

    /**
     * 为一个新的随机起点扣除一次尝试额度, 后续被前置规则拒绝也保留这次计数.
     *
     * @return true 表示本次起点已经计数, false 表示次数耗尽, 超时或搜索已停止
     */
    boolean tryAttempt() {
        if (!this.active() || this.attempts >= this.maxAttempts || this.checks >= this.maxChecks) return false;
        this.attempts++;
        return true;
    }

    /**
     * 为即将进入的一列或即将扫描的一个 Y 层扣除一次检查额度.
     * 一层对应的多条安全规则共用这一次计数, 异步等待后继续同一项工作也沿用原计数.
     *
     * @return true 表示本次工作已经计数, false 表示检查次数耗尽, 超时或搜索已停止
     */
    boolean tryCheck() {
        if (!this.active() || this.checks >= this.maxChecks) return false;
        this.checks++;
        return true;
    }

    /**
     * 判断能否开始检查下一列, 供整轮搜索在取出附近坐标前检查预算.
     *
     * @return 还有检查额度, 尚未超时且未停止时为 true
     */
    boolean canCheck() {
        return this.active() && this.checks < this.maxChecks;
    }

    /**
     * 判断已经开始的工作能否继续, 包括最后一次计数对应的规则链.
     * 是否还有额度开始下一项工作, 分别由 tryAttempt 和 tryCheck 判断.
     *
     * @return 搜索尚未停止且仍在时限内时为 true
     */
    boolean active() {
        return this.remainingNanos() > 0;
    }

    /**
     * 取得整轮剩余时限, 供调用方安排超时任务或检查异步等待后的剩余时间.
     * 按单调时钟的已用时长计算, 区域准备消耗的时间也会从返回值扣除.
     *
     * @return 剩余纳秒数, 超时或停止后为 0
     */
    long remainingNanos() {
        if (this.stopped) return 0;
        long elapsed = System.nanoTime() - this.startedAt;
        return elapsed >= this.timeoutNanos ? 0 : this.timeoutNanos - elapsed;
    }

    /**
     * 标记搜索已经结束, 后续计数和异步继续操作都会观察到停止状态.
     * 可由正常完成, 异常完成或取消回调调用, 多次调用保持相同结果.
     */
    void stop() {
        this.stopped = true;
    }
}