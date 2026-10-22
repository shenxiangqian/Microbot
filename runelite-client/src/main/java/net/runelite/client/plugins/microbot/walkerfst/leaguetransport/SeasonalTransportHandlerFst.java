package net.runelite.client.plugins.microbot.walkerfst.leaguetransport;

import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportFst;

/**
 * Pluggable seasonal row executor for {@link net.runelite.client.plugins.microbot.walkerfst.Rs2WalkerFst}.
 * Order is defined by the list passed to {@link net.runelite.client.plugins.microbot.walkerfst.Rs2WalkerFst#setSeasonalTransportHandlers}.
 */
public interface SeasonalTransportHandlerFst
{
	/**
	 * Cheap gate before {@link #tryUse(Transport)} — should not open UI or block.
	 */
	boolean matches(TransportFst transport);

	/**
	 * Attempts this handler's seasonal flow (not on the RuneLite client thread — same rules as Leagues/MoA).
	 *
	 * @return {@code true} when this handler consumed the transport attempt successfully
	 */
	boolean tryUse(TransportFst transport);
}
