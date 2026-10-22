package net.runelite.client.plugins.microbot.walkerfst;

/** Interchangeable route-engine boundary used after Microbot resolves an immutable request policy. */
public interface Rs2RoutePlannerFst
{
	/** Stable diagnostic id such as {@code microbot-local} or a pinned upstream revision. */
	String getEngineId();

	/**
	 * Calculate one route. Implementations must reject requests without a resolved policy rather than
	 * consulting mutable Microbot or plugin globals.
	 */
	Rs2RouteResultFst plan(Rs2RouteRequestFst request, Rs2PlanningSnapshotFst snapshot);
}
