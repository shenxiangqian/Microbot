package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 临时目标（interim）补点交接判定：必须“实际走近”目标才允许补点，
 * 不能仅凭切比雪夫距离进入预点击半径就补点。
 */
public class Rs2WalkerInterimHandoffTest
{
	private static final int RUN_HANDOFF = Rs2Walker.INTERIM_RUN_PRECLICK_TILES;
	private static final WorldPoint PLAYER_START = new WorldPoint(3200, 3200, 0);

	@Test
	public void diagonalClickInsideChebyshevBandButNoProgressIsNotReady()
	{
		// 斜向点击：欧氏距离约 9.9，切比雪夫距离只有 7，已落在跑步预点击半径(8)的方框内
		WorldPoint interim = new WorldPoint(3207, 3207, 0);
		int initial = Rs2WalkerMovement.distanceToInterimOrMax(interim, PLAYER_START);

		assertFalse(Rs2WalkerMovement.routeInterimReadyForContinuation(
			interim, PLAYER_START, initial, false, RUN_HANDOFF));
	}

	@Test
	public void ready_whenProgressedEnoughAndInsideEuclideanRadius()
	{
		WorldPoint interim = new WorldPoint(3212, 3200, 0);
		int initial = Rs2WalkerMovement.distanceToInterimOrMax(interim, PLAYER_START);
		WorldPoint nowAt = new WorldPoint(3205, 3200, 0);

		assertTrue(Rs2WalkerMovement.routeInterimReadyForContinuation(
			interim, nowAt, initial, false, RUN_HANDOFF));
	}

	@Test
	public void notReady_whenProgressBelowMinimum()
	{
		WorldPoint interim = new WorldPoint(3209, 3200, 0);
		int initial = Rs2WalkerMovement.distanceToInterimOrMax(interim, PLAYER_START);
		// 只走近 1 格
		WorldPoint nowAt = new WorldPoint(3201, 3200, 0);

		assertFalse(Rs2WalkerMovement.routeInterimReadyForContinuation(
			interim, nowAt, initial, false, RUN_HANDOFF));
	}

	@Test
	public void notReady_whenInsideChebyshevBoxButOutsideEuclideanRadius()
	{
		// 玩家在目标斜向 (6,6)：切比雪夫 6 在半径 8 内，但欧氏约 8.5 在半径外
		WorldPoint interim = new WorldPoint(3220, 3220, 0);
		WorldPoint nowAt = new WorldPoint(3214, 3214, 0);

		assertFalse(Rs2WalkerMovement.routeInterimReadyForContinuation(
			interim, nowAt, 20, false, RUN_HANDOFF));
	}

	@Test
	public void notReady_forRecoveryOwnedInterim()
	{
		WorldPoint interim = new WorldPoint(3212, 3200, 0);
		WorldPoint nowAt = new WorldPoint(3208, 3200, 0);

		assertFalse(Rs2WalkerMovement.routeInterimReadyForContinuation(
			interim, nowAt, 12, true, RUN_HANDOFF));
	}

	@Test
	public void notReady_whenInitialDistanceUnknown()
	{
		WorldPoint interim = new WorldPoint(3212, 3200, 0);
		WorldPoint nowAt = new WorldPoint(3209, 3200, 0);

		assertFalse(Rs2WalkerMovement.routeInterimReadyForContinuation(
			interim, nowAt, Integer.MAX_VALUE, false, RUN_HANDOFF));
	}

	@Test
	public void notReady_acrossPlanes()
	{
		WorldPoint interim = new WorldPoint(3212, 3200, 1);

		assertFalse(Rs2WalkerMovement.routeInterimReadyForContinuation(
			interim, PLAYER_START, 12, false, RUN_HANDOFF));
	}

	@Test
	public void holdWhileCloseSkipsCloseClearButKeepsOtherClears()
	{
		WorldPoint interim = new WorldPoint(3203, 3200, 0);
		long now = 10_000L;

		// 靠近目标：普通情况清除，终点持有时不清除
		assertTrue(Rs2WalkerMovement.shouldClearInterimTarget(
			interim, PLAYER_START, now - 500, now - 100, now, Integer.MAX_VALUE, false));
		assertFalse(Rs2WalkerMovement.shouldClearInterimTarget(
			interim, PLAYER_START, now - 500, now - 100, now, Integer.MAX_VALUE, true));

		// 兜底清除不受持有影响：换平面、进度停滞
		assertTrue(Rs2WalkerMovement.shouldClearInterimTarget(
			new WorldPoint(3203, 3200, 1), PLAYER_START, now - 500, now - 100, now, Integer.MAX_VALUE, true));
		assertTrue(Rs2WalkerMovement.shouldClearInterimTarget(
			interim, PLAYER_START, now - 5_000, now - Rs2Walker.INTERIM_PROGRESS_TIMEOUT_MS - 1, now,
			Integer.MAX_VALUE, true));
	}

	@Test
	public void continuationRetry_notDue_withoutPending()
	{
		assertFalse(Rs2WalkerMovement.continuationRetryDue(0L, 0L, 10_000L, false));
	}

	@Test
	public void continuationRetry_notDue_whileInterimHeld()
	{
		long since = 10_000L;
		assertFalse(Rs2WalkerMovement.continuationRetryDue(since, since, since + 500L, true));
	}

	@Test
	public void continuationRetry_notDue_beforeInterval()
	{
		long since = 10_000L;
		assertFalse(Rs2WalkerMovement.continuationRetryDue(
			since, since, since + Rs2Walker.CONTINUATION_RETRY_INTERVAL_MS - 1, false));
	}

	@Test
	public void continuationRetry_due_afterInterval_insideWindow()
	{
		long since = 10_000L;
		assertTrue(Rs2WalkerMovement.continuationRetryDue(
			since, since, since + Rs2Walker.CONTINUATION_RETRY_INTERVAL_MS, false));
		// 上一次重试之后重新计间隔
		long lastTry = since + 500L;
		assertFalse(Rs2WalkerMovement.continuationRetryDue(since, lastTry, lastTry + 100L, false));
		assertTrue(Rs2WalkerMovement.continuationRetryDue(
			since, lastTry, lastTry + Rs2Walker.CONTINUATION_RETRY_INTERVAL_MS, false));
	}

	@Test
	public void continuationRetry_notDue_afterWindow()
	{
		long since = 10_000L;
		assertFalse(Rs2WalkerMovement.continuationRetryDue(
			since, since, since + Rs2Walker.CONTINUATION_RETRY_WINDOW_MS + 1, false));
	}

	@Test
	public void continuationRetryWindowFitsSeveralRetriesAndIsBounded()
	{
		// 窗口里至少能放下几次重试，否则等于没有重试
		assertTrue(Rs2Walker.CONTINUATION_RETRY_WINDOW_MS >= 4 * Rs2Walker.CONTINUATION_RETRY_INTERVAL_MS);
		// 窗口不能长过“进度停滞”超时，否则重试会掩盖真正的停滞
		assertTrue(Rs2Walker.CONTINUATION_RETRY_WINDOW_MS <= Rs2Walker.INTERIM_PROGRESS_TIMEOUT_MS);
	}

	@Test
	public void zoomedOutReachStaysAboveClickFloorAndBelowClipRisk()
	{
		// 点击上限要高于点击下限，才有抖动空间；同时要低于日志里观察到的被拒绝距离(16.3)
		assertTrue(Rs2Walker.ZOOMED_OUT_MINIMAP_REACH_CAP > Rs2Walker.ROUTE_CLICK_REACH_MIN_TILES);
		assertTrue(Rs2Walker.ZOOMED_OUT_MINIMAP_REACH_CAP < 16);
		// 缩放到最远时，步长被限制在上限内
		assertTrue(Rs2WalkerMovement.zoomAwareMinimapReach(1.0, Rs2Walker.MIN_MINIMAP_REACH_EUCLIDEAN,
			Rs2Walker.ZOOMED_OUT_MINIMAP_REACH_CAP, Rs2Walker.NORMAL_MINIMAP_REACH_EUCLIDEAN)
			== Rs2Walker.ZOOMED_OUT_MINIMAP_REACH_CAP);
	}

	@Test
	public void handoffRadiusIsEuclidean()
	{
		WorldPoint center = new WorldPoint(3200, 3200, 0);

		assertTrue(Rs2WalkerMovement.isWithinRouteHandoffRadius(new WorldPoint(3208, 3200, 0), center, 8));
		assertFalse(Rs2WalkerMovement.isWithinRouteHandoffRadius(new WorldPoint(3206, 3206, 0), center, 8));
		assertFalse(Rs2WalkerMovement.isWithinRouteHandoffRadius(null, center, 8));
	}

	@Test
	public void progressTilesMeasureApproachSinceClick()
	{
		WorldPoint interim = new WorldPoint(3212, 3200, 0);

		// 点击时距离 12 格，现在距离 5 格 → 走近了 7 格
		assertEquals(7, Rs2WalkerMovement.interimProgressTiles(
			interim, new WorldPoint(3207, 3200, 0), 12));
	}

	@Test
	public void progressTilesAreZeroWhenInitialDistanceUnknown()
	{
		WorldPoint interim = new WorldPoint(3212, 3200, 0);

		assertEquals(0, Rs2WalkerMovement.interimProgressTiles(
			interim, new WorldPoint(3207, 3200, 0), Integer.MAX_VALUE));
		assertEquals(0, Rs2WalkerMovement.interimProgressTiles(
			interim, new WorldPoint(3207, 3200, 1), 12));
	}

	@Test
	public void preclickLookaheadIsZeroAtNormalCycle()
	{
		// 回合很快时不能改变原有行为：跑步 9 格、走路 7 格
		assertEquals(0, Rs2WalkerMovement.interimPreclickLookaheadTiles(true, 300.0));
		assertEquals(0, Rs2WalkerMovement.interimPreclickLookaheadTiles(false, 300.0));
		assertEquals(0, Rs2WalkerMovement.interimPreclickLookaheadTiles(true, 0.0));
		assertEquals(Rs2Walker.INTERIM_RUN_PRECLICK_TILES,
			Rs2WalkerMovement.interimPreclickTiles(true, 0.0));
		assertEquals(Rs2Walker.INTERIM_PRECLICK_TILES,
			Rs2WalkerMovement.interimPreclickTiles(false, 0.0));
	}

	@Test
	public void slowCycleHandsOffEarlier()
	{
		// 回合被拉长到 600ms：跑步约走 2 格，换算出的提前量是 1 格
		assertEquals(1, Rs2WalkerMovement.interimPreclickLookaheadTiles(true, 600.0));
		// 提前量本身不被截断，但总半径被硬上限压住，不能顶到点击距离
		assertEquals(Rs2WalkerMovement.INTERIM_PRECLICK_MAX_TILES,
			Rs2WalkerMovement.interimPreclickTiles(true, 600.0));
		// 走路慢一半，同样的回合耗时只需要半格余量，向上取整后仍是 1 格
		assertEquals(1, Rs2WalkerMovement.interimPreclickLookaheadTiles(false, 900.0));
		// 走路基础半径更低，慢回合时还能真正用满提前量
		assertEquals(Rs2Walker.INTERIM_PRECLICK_TILES + 1,
			Rs2WalkerMovement.interimPreclickTiles(false, 900.0));
	}

	@Test
	public void preclickLookaheadIsBounded()
	{
		// 外部停顿不能把预点击半径推到"点完立刻又交接"
		assertEquals(Rs2WalkerMovement.INTERIM_PRECLICK_LOOKAHEAD_CAP_TILES,
			Rs2WalkerMovement.interimPreclickLookaheadTiles(true, 60_000.0));
		assertEquals(Rs2WalkerMovement.INTERIM_PRECLICK_MAX_TILES,
			Rs2WalkerMovement.interimPreclickTiles(true, 60_000.0));
		// 交接半径必须严格小于点击距离（normalMinimapReach() - 1 = 10），否则点完立刻又交接
		assertTrue(Rs2WalkerMovement.interimPreclickTiles(true, 60_000.0)
			< Rs2Walker.NORMAL_MINIMAP_REACH_EUCLIDEAN - 1);
		assertTrue(Rs2WalkerMovement.interimPreclickTiles(true, 60_000.0)
			< Rs2Walker.ZOOMED_OUT_MINIMAP_REACH_CAP);
	}
}
