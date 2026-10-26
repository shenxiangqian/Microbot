package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebWalkMovementPlannerTest
{
	private static final WorldPoint PLAYER = new WorldPoint(3200, 3200, 0);
	private static final WorldPoint FAR = new WorldPoint(3212, 3200, 0);
	private static final WorldPoint NEAR = new WorldPoint(3204, 3200, 0);
	private static final int HANDOFF = 6;

	@Test
	public void noInterimClicksNew()
	{
		assertEquals(WebWalkMovementPlanner.Action.CLICK_NEW,
			WebWalkMovementPlanner.decide(PLAYER, null, false, 0, HANDOFF));
	}

	@Test
	public void nullPlayerClicksNew()
	{
		assertEquals(WebWalkMovementPlanner.Action.CLICK_NEW,
			WebWalkMovementPlanner.decide(null, FAR, true, 0, HANDOFF));
	}

	@Test
	public void differentPlaneClicksNew()
	{
		assertEquals(WebWalkMovementPlanner.Action.CLICK_NEW,
			WebWalkMovementPlanner.decide(PLAYER, new WorldPoint(3212, 3200, 1), true, 0, HANDOFF));
	}

	@Test
	public void movingTowardFarInterimWaits()
	{
		assertEquals(WebWalkMovementPlanner.Action.WAIT_IN_FLIGHT,
			WebWalkMovementPlanner.decide(PLAYER, FAR, true, 5_000, HANDOFF));
	}

	@Test
	public void justClickedButNotYetMovingWaitsDuringGrace()
	{
		assertEquals(WebWalkMovementPlanner.Action.WAIT_IN_FLIGHT,
			WebWalkMovementPlanner.decide(PLAYER, FAR, false, 300, HANDOFF));
	}

	@Test
	public void idleAfterGraceClicksNew()
	{
		assertEquals(WebWalkMovementPlanner.Action.CLICK_NEW,
			WebWalkMovementPlanner.decide(PLAYER, FAR, false,
				WebWalkMovementPlanner.MOVEMENT_START_GRACE_MS, HANDOFF));
	}

	@Test
	public void withinHandoffDistanceClicksNewEvenWhileMoving()
	{
		assertEquals(WebWalkMovementPlanner.Action.CLICK_NEW,
			WebWalkMovementPlanner.decide(PLAYER, NEAR, true, 100, HANDOFF));
	}

	@Test
	public void movedSinceClickBypassesGraceWindow()
	{
		// 点击之后玩家确实动过 → 这次点击已生效，不该再把起走宽限期等满
		assertEquals(WebWalkMovementPlanner.Action.CLICK_NEW,
			WebWalkMovementPlanner.decide(PLAYER, FAR, false, 300, HANDOFF, true));
	}

	@Test
	public void noMovementYetStillWaitsDuringGrace()
	{
		// 点击之后玩家还没动过 → 宽限期仍然生效，避免重复点击
		assertEquals(WebWalkMovementPlanner.Action.WAIT_IN_FLIGHT,
			WebWalkMovementPlanner.decide(PLAYER, FAR, false, 300, HANDOFF, false));
	}

	@Test
	public void handoffRadiusIsEuclideanNotChebyshev()
	{
		// 对角线 (6,6)：切比雪夫 6 在半径 6 内，但欧氏约 8.5 在半径外 → 不算已走近
		WorldPoint diagonalFar = new WorldPoint(3206, 3206, 0);

		assertEquals(WebWalkMovementPlanner.Action.WAIT_IN_FLIGHT,
			WebWalkMovementPlanner.decide(PLAYER, diagonalFar, true, 5_000, HANDOFF));
	}

	private static final WorldPoint PATH_END = new WorldPoint(3230, 3200, 0);
	private static final WorldPoint TARGET = new WorldPoint(3231, 3200, 0);

	@Test
	public void clickNearPathEndCoversRouteEnd()
	{
		assertTrue(WebWalkMovementPlanner.clickCoversRouteEnd(
			new WorldPoint(3229, 3200, 0), PATH_END, TARGET));
	}

	@Test
	public void clickNearTargetCoversRouteEndEvenWhenPathEndIsShort()
	{
		WorldPoint shortPathEnd = new WorldPoint(3210, 3200, 0);
		assertTrue(WebWalkMovementPlanner.clickCoversRouteEnd(
			new WorldPoint(3230, 3200, 0), shortPathEnd, TARGET));
	}

	@Test
	public void clickFarFromEndDoesNotCoverRouteEnd()
	{
		assertFalse(WebWalkMovementPlanner.clickCoversRouteEnd(
			new WorldPoint(3215, 3200, 0), PATH_END, TARGET));
	}

	@Test
	public void clickOnOtherPlaneDoesNotCoverRouteEnd()
	{
		assertFalse(WebWalkMovementPlanner.clickCoversRouteEnd(
			new WorldPoint(3230, 3200, 1), PATH_END, TARGET));
	}

	@Test
	public void nullClickDoesNotCoverRouteEnd()
	{
		assertFalse(WebWalkMovementPlanner.clickCoversRouteEnd(null, PATH_END, TARGET));
	}

	@Test
	public void holdsWhileMovingTowardTerminal()
	{
		assertTrue(WebWalkMovementPlanner.holdTerminalApproach(true, false, true, 5_000, 5_000));
	}

	@Test
	public void holdsDuringMovementStartGrace()
	{
		assertTrue(WebWalkMovementPlanner.holdTerminalApproach(true, false, false, 300, -1));
	}

	@Test
	public void holdsBrieflyAfterLastObservedMovement()
	{
		assertTrue(WebWalkMovementPlanner.holdTerminalApproach(true, false, false, 5_000, 200));
	}

	@Test
	public void releasesOnceSettledAndGraceExpired()
	{
		assertFalse(WebWalkMovementPlanner.holdTerminalApproach(true, false, false,
			WebWalkMovementPlanner.MOVEMENT_START_GRACE_MS,
			WebWalkMovementPlanner.TERMINAL_SETTLE_MS));
	}

	@Test
	public void neverHoldsNonTerminalInterim()
	{
		assertFalse(WebWalkMovementPlanner.holdTerminalApproach(false, false, true, 100, 100));
	}

	@Test
	public void neverHoldsRecoveryOwnedInterim()
	{
		assertFalse(WebWalkMovementPlanner.holdTerminalApproach(true, true, true, 100, 100));
	}
}
