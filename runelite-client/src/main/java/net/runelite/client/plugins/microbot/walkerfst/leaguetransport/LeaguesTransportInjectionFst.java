package net.runelite.client.plugins.microbot.walkerfst.leaguetransport;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportTypeFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.WorldPointUtilFst;
import net.runelite.client.plugins.microbot.walkerfst.WebWalkLogFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.PrimitiveIntHashMapFst;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pathfinder injection for Leagues Area and catalog transports.
 */
final class LeaguesTransportInjectionFst
{
	private LeaguesTransportInjectionFst()
	{
	}

	private static volatile EnumSet<LeaguesRegionFst> lastInjectedUnlockedForBlacklistPrune = null;

	static void injectLeaguesTransports(
			Predicate<TransportFst> transportUsable,
			Rs2LeaguesTransportFst.LeaguesContext ctx,
			Set<TransportFst> usableTeleports,
			Map<WorldPoint, Set<TransportFst>> transports,
			PrimitiveIntHashMapFst<Set<TransportFst>> transportsPacked,
			Map<TransportTypeFst, int[]> typeStats)
	{
		if (transportUsable == null || ctx == null || !ctx.isActive() || ctx.getUnlockedRegions().isEmpty()
				|| usableTeleports == null || transports == null || transportsPacked == null || typeStats == null)
		{
			return;
		}

		EnumSet<LeaguesRegionFst> unlockedNow = EnumSet.copyOf(ctx.getUnlockedRegions());
		EnumSet<LeaguesRegionFst> prevUnlocked = lastInjectedUnlockedForBlacklistPrune;
		if (prevUnlocked != null)
		{
			for (LeaguesRegionFst r : unlockedNow)
			{
				if (!prevUnlocked.contains(r))
				{
					LeaguesTransportPersistenceFst.invalidateBlacklistFor(r);
				}
			}
		}
		lastInjectedUnlockedForBlacklistPrune = unlockedNow;

		// Match monolith inject order: calibrate landings before area + catalog inject so cache is ready.
		// Uses same unlock snapshot as inject below (tickLeaguesCalibration still rate-limits standalone probes).
		LeaguesTransportTeleportFst.calibrateMissingLandingsAsync(unlockedNow);

		injectLeaguesAreaTeleports(transportUsable, ctx.getUnlockedRegions(), usableTeleports, typeStats);
		injectLeaguesCatalogTransports(transportUsable, ctx.getUnlockedRegions(), usableTeleports, transports, transportsPacked, typeStats);
	}

	private static boolean mergeOriginlessTeleportByBestDuration(Set<TransportFst> usableTeleports, TransportFst candidate)
	{
		if (candidate == null || candidate.getOrigin() != null || candidate.getDestination() == null)
		{
			return false;
		}
		int p = WorldPointUtilFst.packWorldPoint(candidate.getDestination());
		int minDur = candidate.getDuration();
		for (TransportFst o : usableTeleports)
		{
			if (o == null || o.getOrigin() != null || o.getDestination() == null)
			{
				continue;
			}
			if (WorldPointUtilFst.packWorldPoint(o.getDestination()) == p)
			{
				minDur = Math.min(minDur, o.getDuration());
			}
		}
		if (candidate.getDuration() > minDur)
		{
			return false;
		}
		usableTeleports.removeIf(o -> o != null && o.getOrigin() == null && o.getDestination() != null
				&& WorldPointUtilFst.packWorldPoint(o.getDestination()) == p);
		return usableTeleports.add(candidate);
	}

	private static void injectLeaguesAreaTeleports(
			Predicate<TransportFst> transportUsable,
			EnumSet<LeaguesRegionFst> unlockedLeaguesRegions,
			Set<TransportFst> usableTeleports,
			Map<TransportTypeFst, int[]> typeStats)
	{
		int before = usableTeleports.size();
		int added = 0;

		for (LeaguesRegionFst region : unlockedLeaguesRegions)
		{
			Optional<WorldPoint> landingOpt = LeaguesTransportPersistenceFst.getCachedRegionLanding(region);
			if (!landingOpt.isPresent())
			{
				continue;
			}

			WorldPoint landing = landingOpt.get();
			TransportFst t = new TransportFst(
					landing,
					"Leagues Area: " + region.getDisplayName(),
					TransportTypeFst.SEASONAL_TRANSPORT,
					true,
					31,
					java.util.Collections.emptySet());
			if (!transportUsable.test(t))
			{
				continue;
			}
			if (mergeOriginlessTeleportByBestDuration(usableTeleports, t))
			{
				added++;
			}
		}

		if (added > 0)
		{
			int[] stats = typeStats.computeIfAbsent(TransportTypeFst.SEASONAL_TRANSPORT, k -> new int[]{0, 0, 0});
			stats[0] += added;
			stats[1] += added;
			WebWalkLogFst.leagues("inject_area n={} originless {} -> {}",
					added, before, usableTeleports.size());
		}
	}

	private static void injectLeaguesCatalogTransports(
			Predicate<TransportFst> transportUsable,
			EnumSet<LeaguesRegionFst> unlockedLeaguesRegions,
			Set<TransportFst> usableTeleports,
			Map<WorldPoint, Set<TransportFst>> transports,
			PrimitiveIntHashMapFst<Set<TransportFst>> transportsPacked,
			Map<TransportTypeFst, int[]> typeStats)
	{
		int beforeOriginless = usableTeleports.size();
		int addedOriginless = 0;
		int addedOriginBased = 0;

		java.util.List<TransportFst> catalog = LeaguesTransportObservationsFst.loadCatalogTransports(unlockedLeaguesRegions);
		for (TransportFst t : catalog)
		{
			if (t == null || t.getDestination() == null)
			{
				continue;
			}

			if (!transportUsable.test(t))
			{
				continue;
			}

			TransportTypeFst tt = t.getType();
			if (t.getOrigin() == null)
			{
				if (mergeOriginlessTeleportByBestDuration(usableTeleports, t))
				{
					addedOriginless++;
					if (tt != null)
					{
						int[] stats = typeStats.computeIfAbsent(tt, k -> new int[]{0, 0, 0});
						stats[0] += 1;
						stats[1] += 1;
					}
				}
			}
			else
			{
				transports.computeIfAbsent(t.getOrigin(), k -> new HashSet<>()).add(t);

				int packedOrigin = WorldPointUtilFst.packWorldPoint(t.getOrigin());
				Set<TransportFst> packedSet = transportsPacked.get(packedOrigin);
				if (packedSet == null)
				{
					packedSet = new HashSet<>();
					transportsPacked.put(packedOrigin, packedSet);
				}
				packedSet.add(t);
				addedOriginBased++;
				if (tt != null)
				{
					int[] stats = typeStats.computeIfAbsent(tt, k -> new int[]{0, 0, 0});
					stats[0] += 1;
					stats[1] += 1;
				}
			}
		}

		if (addedOriginless + addedOriginBased > 0)
		{
			WebWalkLogFst.leagues("inject_catalog n={} originlessAdd={} originAdd={} usable {} -> {}",
					addedOriginless + addedOriginBased,
					addedOriginless,
					addedOriginBased,
					beforeOriginless,
					usableTeleports.size());
		}
	}

	static boolean isTransportAllowed(Rs2LeaguesTransportFst.LeaguesContext ctx, TransportFst transport)
	{
		if (transport == null)
		{
			return false;
		}
		if (ctx == null || !ctx.isActive())
		{
			return true;
		}
		WorldPoint dest = transport.getDestination();
		if (dest == null)
		{
			return transport.getType() != TransportTypeFst.SEASONAL_TRANSPORT;
		}
		int packed = WorldPointUtilFst.packWorldPoint(dest);
		if (LeaguesTransportPersistenceFst.isDestinationBlacklisted(packed))
		{
			return false;
		}
		LeaguesRegionFst learned = LeaguesTransportPersistenceFst.getBlacklistedDestinationRegionsSnapshot().get(packed);
		return learned == null || ctx.getUnlockedRegions().contains(learned);
	}
}
