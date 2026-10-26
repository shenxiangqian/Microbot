package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Fst 版移动派发规则的单测：选点（最远可达点）、回退（沿路径往回找）、
 * 近目标改走主屏幕，以及常量与 Fst 保持一致。
 */
public class WalkerClickPolicyTest
{
	private static final WorldPoint PLAYER = new WorldPoint(3200, 3200, 0);

	private static Set<WorldPoint> reachable(WorldPoint... points)
	{
		return new HashSet<>(Arrays.asList(points));
	}

	private static List<WorldPoint> path(WorldPoint... points)
	{
		return new ArrayList<>(Arrays.asList(points));
	}

	@Test
	public void constantsMatchFst()
	{
		// Fst: MINIMAP_COMMAND_RADIUS = 10 / STATE_WAIT_MS = 750 / CANVAS_ROUTE_RADIUS = 5
		assertEquals(10, WalkerClickPolicy.MINIMAP_COMMAND_RADIUS);
		assertEquals(750, WalkerClickPolicy.STATE_WAIT_MS);
		assertEquals(5, WalkerClickPolicy.CANVAS_ROUTE_RADIUS);
		assertEquals(5.0, WalkerClickPolicy.MINIMAP_ZOOM, 0.0001);
	}

	@Test
	public void selectsFurthestReachablePointInsideRadius()
	{
		WorldPoint first = new WorldPoint(3203, 3200, 0);
		WorldPoint middle = new WorldPoint(3207, 3200, 0);
		WorldPoint far = new WorldPoint(3210, 3200, 0);
		List<WorldPoint> route = path(PLAYER, first, middle, far);

		WorldPoint selected = WalkerClickPolicy.selectForwardCandidate(
			route, PLAYER, reachable(first, middle, far), WalkerClickPolicy.MINIMAP_COMMAND_RADIUS);

		assertNotNull(selected);
		assertEquals(far, selected);
	}

	@Test
	public void unreachablePointIsNeverSelected()
	{
		WorldPoint reachablePoint = new WorldPoint(3205, 3200, 0);
		WorldPoint walledPoint = new WorldPoint(3209, 3200, 0);
		List<WorldPoint> route = path(PLAYER, reachablePoint, walledPoint);

		WorldPoint selected = WalkerClickPolicy.selectForwardCandidate(
			route, PLAYER, reachable(reachablePoint), WalkerClickPolicy.MINIMAP_COMMAND_RADIUS);

		assertNotNull(selected);
		assertEquals(reachablePoint, selected);
	}

	@Test
	public void stopsAtFirstPointOutsideRadius()
	{
		// Fst 在扫描到第一个超出半径的点时 break，不再看后面的点。
		WorldPoint inside = new WorldPoint(3208, 3200, 0);
		WorldPoint outside = new WorldPoint(3215, 3200, 0);
		WorldPoint later = new WorldPoint(3216, 3200, 0);
		List<WorldPoint> route = path(PLAYER, inside, outside, later);

		WorldPoint selected = WalkerClickPolicy.selectForwardCandidate(
			route, PLAYER, reachable(inside, outside, later), WalkerClickPolicy.MINIMAP_COMMAND_RADIUS);

		assertNotNull(selected);
		assertEquals(inside, selected);
	}

	@Test
	public void returnsNullWhenNothingIsReachable()
	{
		WorldPoint only = new WorldPoint(3204, 3200, 0);
		List<WorldPoint> route = path(PLAYER, only);

		assertNull(WalkerClickPolicy.selectForwardCandidate(
			route, PLAYER, reachable(), WalkerClickPolicy.MINIMAP_COMMAND_RADIUS));
	}

	@Test
	public void closestForwardIndexPrefersReachablePoint()
	{
		WorldPoint nearUnreachable = new WorldPoint(3201, 3200, 0);
		WorldPoint fartherReachable = new WorldPoint(3206, 3200, 0);
		List<WorldPoint> route = path(nearUnreachable, fartherReachable);

		int index = WalkerClickPolicy.closestForwardIndex(route, PLAYER, reachable(fartherReachable));

		assertEquals(1, index);
	}

	@Test
	public void closestForwardIndexFallsBackToAnyPoint()
	{
		WorldPoint near = new WorldPoint(3201, 3200, 0);
		List<WorldPoint> route = path(near);

		int index = WalkerClickPolicy.closestForwardIndex(route, PLAYER, reachable());

		assertEquals(0, index);
	}

	@Test
	public void fallbackWalksBackToNearestClickablePoint()
	{
		WorldPoint current = new WorldPoint(3202, 3200, 0);
		WorldPoint middle = new WorldPoint(3206, 3200, 0);
		WorldPoint requested = new WorldPoint(3209, 3200, 0);
		List<WorldPoint> route = path(current, middle, requested);

		WorldPoint fallback = WalkerClickPolicy.fallbackAlongPath(route, PLAYER,
			reachable(current, middle, requested), 2,
			WalkerClickPolicy.MINIMAP_COMMAND_RADIUS,
			point -> !point.equals(requested));

		assertNotNull(fallback);
		assertEquals(middle, fallback);
	}

	@Test
	public void fallbackReturnsNullWhenNothingIsClickable()
	{
		WorldPoint current = new WorldPoint(3202, 3200, 0);
		List<WorldPoint> route = path(current);

		WorldPoint fallback = WalkerClickPolicy.fallbackAlongPath(route, PLAYER,
			reachable(current), 0, WalkerClickPolicy.MINIMAP_COMMAND_RADIUS, point -> false);

		assertNull(fallback);
	}

	@Test
	public void prefersCanvasOnlyForCloseTargets()
	{
		assertTrue(WalkerClickPolicy.shouldPreferCanvas(PLAYER, new WorldPoint(3205, 3200, 0)));
		assertTrue(WalkerClickPolicy.shouldPreferCanvas(PLAYER, new WorldPoint(3203, 3203, 0)));
		assertFalse(WalkerClickPolicy.shouldPreferCanvas(PLAYER, new WorldPoint(3206, 3200, 0)));
		assertFalse(WalkerClickPolicy.shouldPreferCanvas(PLAYER, new WorldPoint(3205, 3200, 1)));
	}

	@Test
	public void withinEuclideanUsesCircleNotChebyshevBox()
	{
		WorldPoint diagonal = new WorldPoint(3206, 3206, 0);
		// 切比雪夫 6 在 6 格方框内，但欧氏约 8.49，应判为超出。
		assertFalse(WalkerClickPolicy.withinEuclidean(diagonal, PLAYER, 6));
		assertTrue(WalkerClickPolicy.withinEuclidean(diagonal, PLAYER, 9));
		assertFalse(WalkerClickPolicy.withinEuclidean(diagonal, PLAYER, -1));
	}
}
