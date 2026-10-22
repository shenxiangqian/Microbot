package net.runelite.client.plugins.microbot.walkerfst.leaguetransport;

import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportFst;

import java.util.List;

/**
 * Built-in {@link SeasonalTransportHandler} instances and default ordering.
 */
public final class SeasonalTransportHandlersFst
{
	private SeasonalTransportHandlersFst()
	{
	}

	public static final SeasonalTransportHandlerFst LEAGUES_AREA = new SeasonalTransportHandlerFst()
	{
		@Override
		public boolean matches(TransportFst transport)
		{
			return Rs2LeaguesTransportFst.matchesLeaguesAreaTransportPrefix(transport);
		}

		@Override
		public boolean tryUse(TransportFst transport)
		{
			return Rs2LeaguesTransportFst.tryHandleLeaguesAreaTransport(transport);
		}
	};

	public static List<SeasonalTransportHandlerFst> defaultHandlerList()
	{
		return List.of(LEAGUES_AREA);
	}
}
