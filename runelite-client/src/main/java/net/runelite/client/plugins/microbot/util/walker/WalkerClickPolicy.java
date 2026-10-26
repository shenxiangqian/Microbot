package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Fst 版（{@code Rs2WalkerFst} / {@code WebWalkExecutorFst} / {@code RuneLiteWebWalkRuntimeFst}）的移动派发规则。
 *
 * <p>Fst 版跑起来比原版流畅，关键不在“点得多快”，而在三点：</p>
 * <ol>
 *   <li><b>选点</b>：只点“玩家出发做 BFS 后确认走得到”的路径点，且不超过
 *       {@link #MINIMAP_COMMAND_RADIUS} 格；点不到就沿路径往回退，绝不点路线之外的点。</li>
 *   <li><b>落点</b>：小地图缩放固定 {@link #MINIMAP_ZOOM} 像素/格，坐标换算与落点一一对应，
 *       点击不会因为“每格不到一个像素”而偏出目标格。</li>
 *   <li><b>节奏</b>：一次点击只做一件事，然后等“状态真的变了”（最多 {@link #STATE_WAIT_MS}），
 *       而不是先睡满一段再轮询。</li>
 * </ol>
 *
 * <p>本类只放纯函数，方便单测；真正发点击的地方在 {@link Rs2WalkerMovement}。</p>
 */
final class WalkerClickPolicy
{
	/** Fst 的 {@code MINIMAP_COMMAND_RADIUS}：小地图点击的世界半径上限（欧氏格）。 */
	static final int MINIMAP_COMMAND_RADIUS = 10;

	/** Fst 的 {@code STATE_WAIT_MS}：一次点击后等待“状态变化”的上限（毫秒）。 */
	static final int STATE_WAIT_MS = 750;

	/** Fst 的 {@code CANVAS_ROUTE_RADIUS}：只有目标在这么近时才允许改用主屏幕点击。 */
	static final int CANVAS_ROUTE_RADIUS = 5;

	/** Fst 的 {@code walkMiniMap(point, 5)}：小地图每格 5 像素。 */
	static final double MINIMAP_ZOOM = 5.0;

	private WalkerClickPolicy()
	{
	}

	/**
	 * Fst 的 {@code selectForwardCandidate}：从离玩家最近的前向路径点开始扫描，
	 * 取半径内<b>最远的可达点</b>。
	 *
	 * <p>与原版的三点差异：</p>
	 * <ul>
	 *   <li>可达是硬条件——BFS 走不到的点一律不点（原版只对“近”的不可达点设闸门）。</li>
	 *   <li>半径固定 {@link #MINIMAP_COMMAND_RADIUS}，不随小地图缩放浮动。</li>
	 *   <li>扫描到第一个超出半径的点就停（Fst 用 break，不是 continue）。</li>
	 * </ul>
	 *
	 * @param path         路线点（Fst 用的是原始路径 rawPath）
	 * @param player       玩家当前位置
	 * @param reachable    玩家出发的 BFS 可达格集合
	 * @param maxEuclidean 允许的最大欧氏距离（格）
	 * @return 选中的路径点；没有合法点时返回 {@code null}
	 */
	static WorldPoint selectForwardCandidate(List<WorldPoint> path,
											 WorldPoint player,
											 Set<WorldPoint> reachable,
											 int maxEuclidean)
	{
		if (path == null || path.isEmpty() || player == null
			|| reachable == null || reachable.isEmpty() || maxEuclidean <= 0)
		{
			return null;
		}
		int startIndex = closestForwardIndex(path, player, reachable);
		if (startIndex < 0)
		{
			return null;
		}
		WorldPoint best = null;
		for (int index = startIndex; index < path.size(); index++)
		{
			WorldPoint point = path.get(index);
			if (point == null || point.getPlane() != player.getPlane()
				|| !withinEuclidean(point, player, maxEuclidean))
			{
				break;
			}
			if (!point.equals(player) && reachable.contains(point))
			{
				best = point;
			}
		}
		return best;
	}

	/**
	 * Fst 的 {@code closestForwardIndex}：优先取“可达且离玩家最近”的路径点；
	 * 一个都不可达时退回“离玩家最近”的路径点，保证扫描仍有一个起点。
	 *
	 * <p>距离相同时取索引更大的那个（Fst 用 {@code reverseOrder} 做 tie-break），
	 * 避免总停在同一条老分支上。</p>
	 */
	static int closestForwardIndex(List<WorldPoint> path,
								   WorldPoint player,
								   Set<WorldPoint> reachable)
	{
		if (path == null || path.isEmpty() || player == null)
		{
			return -1;
		}
		int bestReachableIndex = -1;
		int bestReachableDistance = Integer.MAX_VALUE;
		int bestAnyIndex = -1;
		int bestAnyDistance = Integer.MAX_VALUE;
		for (int index = 0; index < path.size(); index++)
		{
			WorldPoint point = path.get(index);
			if (point == null || point.getPlane() != player.getPlane())
			{
				continue;
			}
			int distance = point.distanceTo2D(player);
			if (distance < bestAnyDistance)
			{
				bestAnyDistance = distance;
				bestAnyIndex = index;
			}
			if (reachable != null && reachable.contains(point) && distance <= bestReachableDistance)
			{
				bestReachableDistance = distance;
				bestReachableIndex = index;
			}
		}
		return bestReachableIndex >= 0 ? bestReachableIndex : bestAnyIndex;
	}

	/**
	 * Fst 的 {@code dispatchRouteMinimap} 回退链：请求点点不到时，沿路径<b>往回</b>找
	 * 第一个既可达、在半径内、又真的点得到的路径点。
	 *
	 * <p>原版在这一步用的是“朝目标方向的直线几何猜测”（radii = 半径/10/8/6/4），
	 * 猜出来的点不一定在路线上，人物会走进旁边的房间再被卡住——这是原版跑起来
	 * 一顿一顿的主要来源之一。Fst 没有这种猜测。</p>
	 *
	 * @param clickable 落点是否真的可点的判定（通常是小地图圆内判定）
	 * @return 回退点；没有合法点时返回 {@code null}
	 */
	static WorldPoint fallbackAlongPath(List<WorldPoint> path,
										 WorldPoint player,
										 Set<WorldPoint> reachable,
										 int requestedIndex,
										 int maxEuclidean,
										 Predicate<WorldPoint> clickable)
	{
		if (path == null || path.isEmpty() || player == null || maxEuclidean <= 0)
		{
			return null;
		}
		int currentIndex = closestForwardIndex(path, player, reachable);
		if (currentIndex < 0)
		{
			return null;
		}
		int from = Math.min(requestedIndex < 0 ? path.size() - 1 : requestedIndex, path.size() - 1);
		for (int index = from; index >= currentIndex; index--)
		{
			WorldPoint candidate = path.get(index);
			if (candidate == null || candidate.equals(player)
				|| candidate.getPlane() != player.getPlane())
			{
				continue;
			}
			if (!withinEuclidean(candidate, player, maxEuclidean))
			{
				continue;
			}
			if (reachable != null && !reachable.isEmpty() && !reachable.contains(candidate))
			{
				continue;
			}
			if (clickable == null || clickable.test(candidate))
			{
				return candidate;
			}
		}
		return null;
	}

	/**
	 * Fst 的 {@code shouldPreferCanvas}：目标离玩家很近时改用主屏幕点击。
	 *
	 * <p>小地图上 2 格的落点离玩家只有几个像素，命中哪一格不确定；主屏幕的地面
	 * 点击是逐格精确的。Fst 因此把 5 格以内的目标交给主屏幕，其余一律走小地图。</p>
	 */
	static boolean shouldPreferCanvas(WorldPoint player, WorldPoint requestedTarget)
	{
		return player != null && requestedTarget != null
			&& player.getPlane() == requestedTarget.getPlane()
			&& player.distanceTo2D(requestedTarget) <= CANVAS_ROUTE_RADIUS;
	}

	/** 欧氏距离判定（切比雪夫会把斜向目标判得过近）。 */
	static boolean withinEuclidean(WorldPoint first, WorldPoint second, int radius)
	{
		if (first == null || second == null || radius < 0
			|| first.getPlane() != second.getPlane())
		{
			return false;
		}
		long dx = (long) first.getX() - second.getX();
		long dy = (long) first.getY() - second.getY();
		return dx * dx + dy * dy <= (long) radius * radius;
	}
}
