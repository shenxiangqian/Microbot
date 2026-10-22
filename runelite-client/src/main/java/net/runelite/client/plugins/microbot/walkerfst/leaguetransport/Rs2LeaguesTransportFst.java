package net.runelite.client.plugins.microbot.walkerfst.leaguetransport;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportTypeFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.PrimitiveIntHashMapFst;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer;
import net.runelite.client.plugins.microbot.walkerfst.Rs2PathApiFst;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;

/**
 * Leagues transport façade. Implementation split across {@code LeaguesTransport*} types (audit T6.2).
 *
 * <p><b>Threading:</b> Do not call {@link #leaguesTeleport} (or {@link #tryHandleLeaguesAreaTransport}) from the RuneLite
 * client thread — they synchronize with client-thread UI and would stall the client.
 */
@Slf4j
public final class Rs2LeaguesTransportFst
{
	/** Defensive bound before sanitize+regex on locked-region chat messages. */
	public static final int LEAGUES_LOCK_CHAT_MAX_NORMALIZE_CHARS = 4096;
	public static final int LEAGUES_LOCK_CHAT_TRUNC_WARN_INTERVAL = 200;

	/**
	 * Max age of last transport attempt for correlating locked-region gamemessages.
	 *
	 * @apiNote Stable for scripts; document in changelog when changing semantics.
	 */
	public static final long LEAGUES_LOCK_CHAT_MAX_ATTEMPT_AGE_MS = 15_000L;

	public static final class LeaguesContext
	{
		private final boolean active;
		private final EnumSet<LeaguesRegionFst> unlockedRegions;

		private LeaguesContext(boolean active, EnumSet<LeaguesRegionFst> unlockedRegions)
		{
			this.active = active;
			this.unlockedRegions = unlockedRegions != null ? unlockedRegions : EnumSet.noneOf(LeaguesRegionFst.class);
		}

		public boolean isActive()
		{
			return active;
		}

		public EnumSet<LeaguesRegionFst> getUnlockedRegions()
		{
			return unlockedRegions;
		}
	}

	private static final LeaguesContext INACTIVE_CONTEXT =
			new LeaguesContext(false, EnumSet.noneOf(LeaguesRegionFst.class));

	private Rs2LeaguesTransportFst()
	{
	}

	public static Integer getLastTransportAttemptPackedDest()
	{
		return LeaguesTransportAttemptsFst.getLastTransportAttemptPackedDest();
	}

	public static String getLastTransportAttemptMethod()
	{
		return LeaguesTransportAttemptsFst.getLastTransportAttemptMethod();
	}

	public static boolean isLeaguesAreaTeleportPending(long maxAgeMs)
	{
		return LeaguesTransportAttemptsFst.isLeaguesAreaTeleportPending(maxAgeMs);
	}

	public static LeaguesTransportAttemptSnapshotFst getLastTransportAttemptSnapshot()
	{
		return LeaguesTransportAttemptsFst.getLastTransportAttemptSnapshot();
	}

	public static void clearLastTransportAttempt()
	{
		LeaguesTransportAttemptsFst.clearLastTransportAttempt();
	}

	/**
	 * Latest transport attempt if younger than {@code maxAgeMs}. Matches {@code 1a5c485}: no geographic or label filtering;
	 * {@code regionCaptured} is ignored at lookup time (region comes from chat when persisting the blacklist row).
	 */
	public static Optional<LeaguesTransportAttemptSnapshotFst> findTransportAttemptForLockedRegionChat(
			String regionCaptured, long nowMs, long maxAgeMs)
	{
		return LeaguesTransportAttemptsFst.findTransportAttemptForLockedRegionChat(regionCaptured, nowMs, maxAgeMs);
	}

	public static void recordTransportAttempt(TransportFst transport)
	{
		LeaguesTransportAttemptsFst.recordTransportAttempt(transport, null);
	}

	public static void recordTransportAttempt(TransportFst transport, String attemptHandler)
	{
		LeaguesTransportAttemptsFst.recordTransportAttempt(transport, attemptHandler);
	}

	public static boolean tryHandleLeaguesAreaTransport(TransportFst transport)
	{
		return tryHandleLeaguesAreaTransportResult(transport).map(LeaguesTeleportResultFst::isSuccess).orElse(false);
	}

	public static boolean matchesLeaguesAreaTransportPrefix(TransportFst transport)
	{
		if (transport == null || transport.getDisplayInfo() == null || !isLeaguesActive())
		{
			return false;
		}
		String displayInfoForPrefix = Rs2TextSanitizer.normalizeAsciiColons(transport.getDisplayInfo());
		Matcher areaPrefix = LeaguesTransportRegionsFst.LEAGUES_AREA_PREFIX.matcher(displayInfoForPrefix);
		return areaPrefix.lookingAt();
	}

	public static Optional<LeaguesTeleportResultFst> tryHandleLeaguesAreaTransportResult(TransportFst transport)
	{
		if (transport == null || transport.getDisplayInfo() == null)
		{
			return Optional.empty();
		}
		if (!isLeaguesActive())
		{
			return Optional.empty();
		}

		String displayInfo = transport.getDisplayInfo();
		String displayInfoForPrefix = Rs2TextSanitizer.normalizeAsciiColons(displayInfo);
		Matcher areaPrefix = LeaguesTransportRegionsFst.LEAGUES_AREA_PREFIX.matcher(displayInfoForPrefix);
		if (!areaPrefix.lookingAt())
		{
			return Optional.empty();
		}

		String regionRaw = areaPrefix.replaceFirst("").trim();
		String sanitizedRegion = Rs2TextSanitizer.sanitizeLeaguesLockedRegionName(regionRaw);
		LeaguesRegionFst region = LeaguesTransportRegionsFst.parseRegionName(sanitizedRegion);
		if (region == null)
		{
			if (log.isDebugEnabled())
			{
				log.debug("Leagues Area transport: parseRegionName miss after sanitize; rawLabel='{}' sanitized='{}'",
						regionRaw, sanitizedRegion);
			}
			return Optional.empty();
		}

		recordTransportAttempt(transport, "LeaguesArea");
		LeaguesTeleportResultFst res = LeaguesTransportTeleportFst.leaguesTeleport(region);
		if (!res.isSuccess() && log.isDebugEnabled())
		{
			log.debug("Leagues Area transport failed: region={} reason={} message={}",
					region, res.getFailureReason(), res.getMessage());
		}
		if (res.isSuccess())
		{
			WorldPoint after = Rs2Player.getWorldLocation();
			if (after != null)
			{
				LeaguesTransportPersistenceFst.persistRegionLanding(region, after);
			}
			clearLastTransportAttempt();
		}
		return Optional.of(res);
	}

	public static void onLockedRegionGameMessage(String msg)
	{
		LeaguesTransportChatFst.onLockedRegionGameMessage(msg);
	}

	public static LeaguesContext leaguesContext()
	{
		if (!isLeaguesActive())
		{
			return INACTIVE_CONTEXT;
		}
		return new LeaguesContext(true, LeaguesTransportTeleportFst.unlockedRegions());
	}

	public static boolean isTransportAllowed(LeaguesContext ctx, TransportFst transport)
	{
		return LeaguesTransportInjectionFst.isTransportAllowed(ctx, transport);
	}

	public static void invalidateContext()
	{
		Rs2PathApiFst.invalidateTransportRefreshCache();
	}

	public static boolean isDestinationBlacklisted(int packedWorldPoint)
	{
		return LeaguesTransportPersistenceFst.isDestinationBlacklisted(packedWorldPoint);
	}

	public static void invalidateBlacklistFor(LeaguesRegionFst region)
	{
		LeaguesTransportPersistenceFst.invalidateBlacklistFor(region);
	}

	public static Map<Integer, LeaguesRegionFst> getBlacklistedDestinationRegionsSnapshot()
	{
		return LeaguesTransportPersistenceFst.getBlacklistedDestinationRegionsSnapshot();
	}

	public static void persistBlacklistDestination(int packedWorldPoint, LeaguesRegionFst region, String method)
	{
		LeaguesTransportPersistenceFst.persistBlacklistDestination(packedWorldPoint, region, method);
	}

	public static void appendTransportAttemptObservation(TransportFst transport, String detail)
	{
		LeaguesTransportObservationsFst.appendTransportObservationInternal("attempt", transport, null, detail);
	}

	public static void appendTransportObservation(String phase, TransportFst transport, boolean success, String detail)
	{
		if (!"result".equals(phase))
		{
			throw new IllegalArgumentException("appendTransportObservation(boolean): phase must be \"result\"");
		}
		LeaguesTransportObservationsFst.appendTransportObservationInternal(phase, transport, Boolean.valueOf(success), detail);
	}

	public static void appendCatalogTransport(LeaguesRegionFst requiredRegion, TransportFst transport, String note)
	{
		LeaguesTransportObservationsFst.appendCatalogTransport(requiredRegion, transport, note);
	}

	public static java.util.List<TransportFst> loadCatalogTransports(EnumSet<LeaguesRegionFst> unlockedRegions)
	{
		return LeaguesTransportObservationsFst.loadCatalogTransports(unlockedRegions);
	}

	public static void injectLeaguesTransports(
			Predicate<TransportFst> transportUsable,
			LeaguesContext ctx,
			Set<TransportFst> usableTeleports,
			Map<WorldPoint, Set<TransportFst>> transports,
			PrimitiveIntHashMapFst<Set<TransportFst>> transportsPacked,
			Map<TransportTypeFst, int[]> typeStats)
	{
		LeaguesTransportInjectionFst.injectLeaguesTransports(
				transportUsable, ctx, usableTeleports, transports, transportsPacked, typeStats);
	}

	public static LeaguesRegionFst parseRegionName(String regionNameRaw)
	{
		return LeaguesTransportRegionsFst.parseRegionName(regionNameRaw);
	}

	public static Optional<String> captureLockedRegionFromChatRaw(String rawForMatch)
	{
		return LeaguesTransportRegionsFst.captureLockedRegionFromChatRaw(rawForMatch);
	}

	public static Optional<String> captureLockedRegionFromSanitizedLower(String sanitizedLower)
	{
		return LeaguesTransportRegionsFst.captureLockedRegionFromSanitizedLower(sanitizedLower);
	}

	public static boolean recordBlockedDestinationFromChat(String regionNameRaw, Integer packedDest, String method)
	{
		return LeaguesTransportRegionsFst.recordBlockedDestinationFromChat(regionNameRaw, packedDest, method);
	}

	public static boolean shouldRecalculatePathAfterLock(String region, Integer packedDest)
	{
		return LeaguesTransportRegionsFst.shouldRecalculatePathAfterLock(region, packedDest);
	}

	public static Optional<WorldPoint> getCachedRegionLanding(LeaguesRegionFst region)
	{
		return LeaguesTransportPersistenceFst.getCachedRegionLanding(region);
	}

	public static void persistRegionLanding(LeaguesRegionFst region, WorldPoint landing)
	{
		LeaguesTransportPersistenceFst.persistRegionLanding(region, landing);
	}

	public static void calibrateMissingLandingsAsync(EnumSet<LeaguesRegionFst> unlockedRegions)
	{
		calibrateMissingLandingsAsync(unlockedRegions, false);
	}

	/**
	 * @param logNoOpWhenFullyCalibrated when {@code true}, logs one INFO line if every unlocked region
	 *                                   already has a persisted landing (e.g. varbit-driven refresh).
	 */
	public static void calibrateMissingLandingsAsync(EnumSet<LeaguesRegionFst> unlockedRegions,
			boolean logNoOpWhenFullyCalibrated)
	{
		LeaguesTransportTeleportFst.calibrateMissingLandingsAsync(unlockedRegions, logNoOpWhenFullyCalibrated);
	}

	public static boolean isTeleportInProgress()
	{
		return LeaguesTransportTeleportFst.isTeleportInProgress();
	}

	public static void tickLeaguesCalibration()
	{
		LeaguesTransportTeleportFst.tickLeaguesCalibration();
	}

	public static void onLogout()
	{
		LeaguesTransportTeleportFst.onLogout();
	}

	public static boolean isLeaguesContext()
	{
		return LeaguesTransportTeleportFst.verifyLeaguesContextOrNull() == null;
	}

	public static boolean isLeaguesActive()
	{
		return net.runelite.client.plugins.microbot.Microbot.getVarbitValue(
				net.runelite.api.gameval.VarbitID.LEAGUE_TYPE) > 0;
	}

	public static EnumSet<LeaguesRegionFst> unlockedRegions()
	{
		return LeaguesTransportTeleportFst.unlockedRegions();
	}

	public static LeaguesTeleportResultFst leaguesTeleport(LeaguesRegionFst region)
	{
		return LeaguesTransportTeleportFst.leaguesTeleport(region);
	}

	public static LeaguesTeleportResultFst leaguesTeleport(LeaguesRegionFst region, int timeoutMs)
	{
		return LeaguesTransportTeleportFst.leaguesTeleport(region, timeoutMs);
	}

	/**
	 * Non-blocking driver for advanced callers. Call {@link #tick()} from script loop until inactive.
	 */
	public static final class LeaguesTeleportDriver extends LeaguesTransportTeleportFst.LeaguesTeleportDriver
	{
		public LeaguesTeleportDriver(LeaguesRegionFst targetRegion)
		{
			super(targetRegion);
		}
	}
}
