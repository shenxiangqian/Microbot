package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;

/**
 * 路径点击移动规划器（纯函数，无副作用，不访问客户端线程）。
 *
 * <p>每次 processWalk 一轮只回答一个问题：上一次点击出的临时目标（interim）现在
 * 应该继续等待，还是应该发出新的点击。这样避免两类问题：
 * <ul>
 *   <li>点击刚发出、服务端尚未开始移动时，被误判为“已停下”而立刻重复点击；</li>
 *   <li>移动途中过早切换目标，造成点击抖动。</li>
 * </ul>
 * 可通过系统属性 {@code -Dmicrobot.walker.movementPlanner=false} 关闭，回退到旧行为。
 */
final class WebWalkMovementPlanner
{
	/**
	 * 点击后给服务端开始移动的宽限时间。
	 *
	 * <p>对齐 Fst 的 {@code MINIMAP_COMMAND_START_GRACE_TICKS = 4}（4 个游戏刻 ≈ 2400ms）：
	 * 服务端已受理、客户端还没起走的那一拍里，绝不能判定"点击失败"并补点——补点会覆盖
	 * 上一次的落点，表现出来就是跑动中的点位抖动和一顿一顿。</p>
	 */
	static final long MOVEMENT_START_GRACE_MS = 2400L;

	static final String ENABLED_PROPERTY = "microbot.walker.movementPlanner";

	enum Action
	{
		/** 继续等待当前临时目标（仍在移动，或刚点击尚在宽限期内）。 */
		WAIT_IN_FLIGHT,
		/** 选择并点击新的目标。 */
		CLICK_NEW
	}

	private WebWalkMovementPlanner()
	{
	}

	/** 点击落点距路线末端/目标不超过该切比雪夫距离，视为“已经点到终点”。 */
	static final int TERMINAL_CLICK_TOLERANCE_TILES = 2;

	/** 玩家最近一次移动之后，需要静止超过该时间才认为真的停下（避免逐格间隙被误判）。 */
	static final long TERMINAL_SETTLE_MS = 700L;

	static boolean enabled()
	{
		return !"false".equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY));
	}

	/**
	 * 这次点击是否已经覆盖了路线终点。
	 *
	 * @param clicked  实际点击落点
	 * @param pathEnd  当前路线（平滑路径）的末端；部分路径时它可能离目标还很远
	 * @param target   行走目标
	 */
	static boolean clickCoversRouteEnd(WorldPoint clicked, WorldPoint pathEnd, WorldPoint target)
	{
		if (clicked == null)
		{
			return false;
		}
		return isNear(clicked, pathEnd) || isNear(clicked, target);
	}

	private static boolean isNear(WorldPoint clicked, WorldPoint anchor)
	{
		return anchor != null
			&& anchor.getPlane() == clicked.getPlane()
			&& anchor.distanceTo2D(clicked) <= TERMINAL_CLICK_TOLERANCE_TILES;
	}

	/**
	 * 欧氏距离半径判定。
	 *
	 * <p>交接判定必须用欧氏而不是切比雪夫：切比雪夫方框在斜向时比欧氏圆大 √2 倍，
	 * 同一把"尺子"贯穿等待、交接、补点三处，才不会出现"等待已结束但交接不成立"的空转窗口。</p>
	 */
	private static boolean isWithinEuclideanRadius(WorldPoint from, WorldPoint to, int radius)
	{
		if (from == null || to == null || from.getPlane() != to.getPlane())
		{
			return false;
		}
		long dx = (long) from.getX() - to.getX();
		long dy = (long) from.getY() - to.getY();
		long boundedRadius = Math.max(0, radius);
		return dx * dx + dy * dy <= boundedRadius * boundedRadius;
	}

	/**
	 * 终点点击是否仍需持有（不交接、不补点）。
	 *
	 * <p>玩家还在移动、刚点击尚在宽限期内、或刚刚还在移动时持有；确认停下后释放，
	 * 之后走原有的“靠近即清除 + 补点”逻辑，此时玩家已站定，补点不会再和奔跑中的路径冲突。
	 *
	 * @param terminal       当前临时目标是否覆盖终点
	 * @param recoveryOwned  目标是否由恢复/空闲补点产生（这类目标沿用超时清除，不持有）
	 * @param moving         玩家当前是否在移动
	 * @param msSinceClick   距上一次点击的毫秒数，未知传 -1
	 * @param msSinceMoved   距玩家最近一次被观察到移动的毫秒数，未知传 -1
	 */
	static boolean holdTerminalApproach(boolean terminal, boolean recoveryOwned, boolean moving,
		long msSinceClick, long msSinceMoved)
	{
		if (!terminal || recoveryOwned)
		{
			return false;
		}
		if (moving)
		{
			return true;
		}
		if (msSinceClick >= 0 && msSinceClick < MOVEMENT_START_GRACE_MS)
		{
			return true;
		}
		return msSinceMoved >= 0 && msSinceMoved < TERMINAL_SETTLE_MS;
	}

	/**
	 * @param player        玩家当前位置
	 * @param interim       上一次点击的临时目标，没有则为 null
	 * @param moving        玩家当前是否在移动
	 * @param msSinceClick  距离上一次点击已过去的毫秒数
	 * @param handoffTiles  距临时目标不超过该欧氏距离时，交接给下一次点击
	 */
	static Action decide(WorldPoint player, WorldPoint interim, boolean moving, long msSinceClick, int handoffTiles)
	{
		return decide(player, interim, moving, msSinceClick, handoffTiles, false);
	}

	/**
	 * @param player        玩家当前位置
	 * @param interim       上一次点击的临时目标，没有则为 null
	 * @param moving        玩家当前是否在移动
	 * @param msSinceClick  距离上一次点击已过去的毫秒数
	 * @param handoffTiles  距临时目标不超过该欧氏距离时，交接给下一次点击
	 * @param movedSinceClick 点击之后是否已经观察到玩家位移；为 true 表示这次点击已经生效
	 */
	static Action decide(WorldPoint player, WorldPoint interim, boolean moving, long msSinceClick, int handoffTiles,
						 boolean movedSinceClick)
	{
		if (player == null || interim == null || player.getPlane() != interim.getPlane())
		{
			return Action.CLICK_NEW;
		}
		if (isWithinEuclideanRadius(player, interim, handoffTiles))
		{
			return Action.CLICK_NEW;
		}
		if (moving)
		{
			return Action.WAIT_IN_FLIGHT;
		}
		// 起走宽限期只保护"点击已发出、但服务端还没让玩家动起来"这一种情况。
		// 一旦观察到玩家确实移动过，就说明这次点击已经生效，再把宽限期等满
		// 只会白白拖慢下一次点击——这正是"跑到停住才点下一个位置"的来源之一。
		if (!movedSinceClick && msSinceClick >= 0 && msSinceClick < MOVEMENT_START_GRACE_MS)
		{
			return Action.WAIT_IN_FLIGHT;
		}
		return Action.CLICK_NEW;
	}
}
