package net.runelite.client.plugins.microbot.util.walker.state;

import java.util.Map;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

/**
 * 分步寻路（{@code Rs2Walker.walkStepFull}）的跨调用状态。
 *
 * <p>阻塞版 {@code processWalk} 的尾循环体里有 13 个局部变量是<b>跨迭代</b>存在的：它们在循环前
 * 初始化，在每一轮迭代里被读改，用来记住“上次点到了哪”“上次部分路径重试发生在何时”“可达格缓存
 * 挂在哪个原点上”等等。把尾循环体提取成单趟（pass）之后，这些变量必须跟着会话走，否则每一步都会
 * 重新初始化，语义就丢了。</p>
 *
 * <p>本对象同时承载分步会话本身的生命周期信息：当前目标、是否已进入过“进入前准备”、是否已经到达终态。
 * 阻塞版不需要这些，所以由 {@code Rs2Walker} 用 {@code stepMode} 区分。</p>
 */
public final class WalkStepSession {

    // ---- 会话身份与生命周期 ----

    /**
     * 调用方最初给出的目标格。
     *
     * <p>与 {@link #target} 区分：sealed rim 改道会把 {@link #target} 换成改道后的 rim 格，但调用方
     * 仍然拿着最初的目标在循环调用。判断「是不是同一段寻路」必须用这个字段，否则改道一次就会
     * 被误判成换了目标、会话被重开。</p>
     */
    public final WorldPoint requestedTarget;

    /** 本段寻路的目标格。sealed rim 改道后会变成改道后的目标。 */
    public WorldPoint target;

    /** 到达判定半径。 */
    public int distance;

    /**
     * 本段寻路最初的「部分路径重试」预算。
     *
     * <p>sealed rim 改道在阻塞版里是递归调用 {@code processWalk(sealedRim, distance, partialRetries)}，
     * 也就是另起一段全新的寻路，重试预算也从最初值重新开始。这个字段保留了那个「最初值」，
     * 供改道时重建预算用。</p>
     */
    public final int partialRetriesInitial;

    /** 是否处于分步模式（由调用方驱动，每趟走一步）。阻塞版为 false。 */
    public boolean stepMode;

    /** 是否已经执行过「进入前准备」（到达判定、setTarget、routeState 重置等）。 */
    public boolean prepared;

    /** 是否已经到达终态（ARRIVED / UNREACHABLE / EXIT）。终态后不再执行任何一趟。 */
    public boolean finished;

    /** 终态内容；仅在 {@link #finished} 为 true 时有效。 */
    public WalkerState finalState;

    /** 分步会话的创建时间（墙钟毫秒），用于诊断。 */
    public final long createdAtMs = System.currentTimeMillis();

    // ---- 原尾循环体的 13 个跨迭代局部变量 ----

    /** 部分路径重试预算的当前余额。 */
    public int partialRetriesWorking;

    /** client 线程读超时重试计数。 */
    public int clientThreadTimeoutRetries;

    /** 寻路器缺失时允许绕开的次数。 */
    public int pathfinderAbsentCarryRetries;

    /** 上一次部分路径重试的墙钟毫秒；0 表示还没发生过。 */
    public long lastPartialRetryAtMs;

    /** 上一次部分路径重试时玩家所在的位置。 */
    public WorldPoint lastPartialRetryAtLoc;

    /** 连续「豁免」迭代计数。 */
    public int consecutiveExemptIterations;

    /** 上一次尝试的小地图点击位置。 */
    public WorldPoint lastAttemptedMinimapClick;

    /** 上一次小地图点击是否成功发出。 */
    public boolean lastAttemptedMinimapClickOk;

    /** 上一次小地图点击的墙钟毫秒。 */
    public long lastAttemptedMinimapClickAtMs;

    /** 寻路器 pending 的起始墙钟毫秒；0 表示当前没有 pending。 */
    public long pathfinderPendingSinceMs;

    /** 可达格缓存（原点 → 距离表）。 */
    public Map<WorldPoint, Integer> reachableTilesCache;

    /** 可达格缓存所对应的玩家原点。 */
    public WorldPoint reachableTilesCacheOrigin;

    /** 尾循环计数器；豁免迭代会把它减回去，因此它不是「已用趟数」而是「预算余额下标」。 */
    public int processWalkTail;

    public WalkStepSession(WorldPoint target, int distance, int partialRetries) {
        this.requestedTarget = target;
        this.target = target;
        this.distance = distance;
        this.partialRetriesInitial = partialRetries;
        this.partialRetriesWorking = partialRetries;
    }

    /**
     * sealed rim 改道时调用：把会话重置成「另起一段寻路」的状态。
     *
     * <p>阻塞版的改道是递归调用 {@code processWalk(sealedRim, distance, partialRetries)}——那是一个全新的
     * 方法调用，13 个局部变量全部从初值重新开始，尾循环预算也重新算 64 次。这里用同样的语义重置本
     * 会话，只是对象本身保留下来（分步模式下调用方持有的是这个对象）。</p>
     *
     * @param newTarget 改道后的目标
     */
    public void restartFor(WorldPoint newTarget) {
        this.target = newTarget;
        this.partialRetriesWorking = partialRetriesInitial;
        this.clientThreadTimeoutRetries = 0;
        this.pathfinderAbsentCarryRetries = 0;
        this.lastPartialRetryAtMs = 0L;
        this.lastPartialRetryAtLoc = null;
        this.consecutiveExemptIterations = 0;
        this.lastAttemptedMinimapClick = null;
        this.lastAttemptedMinimapClickOk = false;
        this.lastAttemptedMinimapClickAtMs = 0L;
        this.pathfinderPendingSinceMs = 0L;
        this.reachableTilesCache = null;
        this.reachableTilesCacheOrigin = null;
        this.processWalkTail = 0;
    }
}
