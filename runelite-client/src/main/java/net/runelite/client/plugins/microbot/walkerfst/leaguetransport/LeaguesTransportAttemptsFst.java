package net.runelite.client.plugins.microbot.walkerfst.leaguetransport;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportTypeFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.WorldPointUtilFst;
import net.runelite.client.plugins.microbot.util.logging.Rs2LogRateLimit;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Recent transport attempt ring and method labels for locked-region chat correlation.
 */
@Slf4j
final class LeaguesTransportAttemptsFst
{
	private LeaguesTransportAttemptsFst()
	{
	}

	private static final int TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX = 256;

	private static volatile LeaguesTransportAttemptSnapshotFst lastTransportAttempt = null;

	private static final int RECENT_TRANSPORT_ATTEMPTS_MAX = 4;
	private static final Object RECENT_TRANSPORT_ATTEMPTS_LOCK = new Object();
	private static final LeaguesTransportAttemptSnapshotFst[] RECENT_TRANSPORT_ATTEMPTS =
			new LeaguesTransportAttemptSnapshotFst[RECENT_TRANSPORT_ATTEMPTS_MAX];
	private static int recentTransportAttemptsCount = 0;

	private static final String LEAGUES_AREA_ATTEMPT_PREFIX =
			TransportTypeFst.SEASONAL_TRANSPORT + ":Leagues Area:";

	private static final AtomicBoolean LOGGED_NULL_SEASONAL_TRANSPORT_TYPE = new AtomicBoolean(false);

	private static boolean isLeaguesActive()
	{
		return Microbot.getVarbitValue(VarbitID.LEAGUE_TYPE) > 0;
	}

	static Integer getLastTransportAttemptPackedDest()
	{
		LeaguesTransportAttemptSnapshotFst s = lastTransportAttempt;
		return s != null ? s.getPackedDest() : null;
	}

	static String getLastTransportAttemptMethod()
	{
		LeaguesTransportAttemptSnapshotFst s = lastTransportAttempt;
		return s != null ? s.getMethod() : null;
	}

	static boolean isLeaguesAreaTeleportPending(long maxAgeMs)
	{
		if (maxAgeMs < 0)
		{
			return false;
		}
		LeaguesTransportAttemptSnapshotFst s = lastTransportAttempt;
		if (s == null)
		{
			return false;
		}
		String m = s.getMethod();
		if (m == null || !m.startsWith(LEAGUES_AREA_ATTEMPT_PREFIX))
		{
			return false;
		}
		return System.currentTimeMillis() - s.getTsMs() <= maxAgeMs;
	}

	static LeaguesTransportAttemptSnapshotFst getLastTransportAttemptSnapshot()
	{
		return lastTransportAttempt;
	}

	static void clearLastTransportAttempt()
	{
		lastTransportAttempt = null;
		synchronized (RECENT_TRANSPORT_ATTEMPTS_LOCK)
		{
			for (int i = 0; i < RECENT_TRANSPORT_ATTEMPTS_MAX; i++)
			{
				RECENT_TRANSPORT_ATTEMPTS[i] = null;
			}
			recentTransportAttemptsCount = 0;
		}
	}

	private static void pushRecentTransportAttempt(LeaguesTransportAttemptSnapshotFst snap)
	{
		if (snap == null)
		{
			return;
		}
		lastTransportAttempt = snap;
		synchronized (RECENT_TRANSPORT_ATTEMPTS_LOCK)
		{
			for (int i = Math.min(RECENT_TRANSPORT_ATTEMPTS_MAX - 1, recentTransportAttemptsCount); i > 0; i--)
			{
				RECENT_TRANSPORT_ATTEMPTS[i] = RECENT_TRANSPORT_ATTEMPTS[i - 1];
			}
			RECENT_TRANSPORT_ATTEMPTS[0] = snap;
			if (recentTransportAttemptsCount < RECENT_TRANSPORT_ATTEMPTS_MAX)
			{
				recentTransportAttemptsCount++;
			}
		}
	}

	/**
	 * Same attribution model as {@code 1a5c485}: bind locked-region chat to the single latest transport attempt
	 * (within {@code maxAgeMs}), not to a filtered ring-buffer entry. Chat supplies the locked shard name when recording;
	 * the attempt supplies {@code packedDest}.
	 *
	 * @param regionCaptured unused (kept for API stability); region text is applied in {@link LeaguesTransportRegions#recordBlockedDestinationFromChat}
	 */
	static Optional<LeaguesTransportAttemptSnapshotFst> findTransportAttemptForLockedRegionChat(
			@SuppressWarnings("unused") String regionCaptured, long nowMs, long maxAgeMs)
	{
		if (maxAgeMs < 0L)
		{
			return Optional.empty();
		}
		LeaguesTransportAttemptSnapshotFst s = lastTransportAttempt;
		if (s == null)
		{
			return Optional.empty();
		}
		long ageMs = nowMs - s.getTsMs();
		if (ageMs > maxAgeMs || ageMs < 0L)
		{
			return Optional.empty();
		}
		return Optional.of(s);
	}

	static void recordTransportAttempt(TransportFst transport, String attemptHandler)
	{
		if (transport == null || transport.getDestination() == null)
		{
			return;
		}
		if (!isLeaguesActive())
		{
			return;
		}

		if (transport.getType() == null)
		{
			if (log.isDebugEnabled() && Rs2LogRateLimit.once(LOGGED_NULL_SEASONAL_TRANSPORT_TYPE))
			{
				String di = transport.getDisplayInfo();
				String sample = di == null ? "" : (di.length() > TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX
						? di.substring(0, TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX) + "…"
						: di);
				Integer packedDest = WorldPointUtilFst.packWorldPoint(transport.getDestination());
				log.debug("recordTransportAttempt: transport.getType() null; check merged transport / TSV. destPacked={} dest={} displayInfoSample='{}'",
						packedDest, transport.getDestination(), sample);
			}
			Integer packed = WorldPointUtilFst.packWorldPoint(transport.getDestination());
			pushRecentTransportAttempt(new LeaguesTransportAttemptSnapshotFst(packed,
					withAttemptHandlerSuffix(buildNullTypeAttemptMethodLabel(transport), attemptHandler),
					System.currentTimeMillis()));
			LeaguesTransportObservationsFst.appendTransportObservationInternal("attempt", transport, null, "");
			return;
		}

		TransportTypeFst type = transport.getType();
		Integer packed = WorldPointUtilFst.packWorldPoint(transport.getDestination());
		String method = withAttemptHandlerSuffix(buildTransportAttemptMethodLabel(transport), attemptHandler);
		pushRecentTransportAttempt(new LeaguesTransportAttemptSnapshotFst(packed, method, System.currentTimeMillis()));

		if (type == TransportTypeFst.SEASONAL_TRANSPORT)
		{
			LeaguesTransportObservationsFst.appendTransportObservationInternal("attempt", transport, null, "");
		}
	}

	private static String withAttemptHandlerSuffix(String method, String attemptHandler)
	{
		if (attemptHandler == null || attemptHandler.isEmpty())
		{
			return method;
		}
		return method + "|handler=" + attemptHandler;
	}

	private static String buildNullTypeAttemptMethodLabel(TransportFst transport)
	{
		String di = transport.getDisplayInfo();
		if (di == null || di.isEmpty())
		{
			return "UNKNOWN:";
		}
		if (di.length() <= TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX)
		{
			return "UNKNOWN:" + di;
		}
		return "UNKNOWN:" + di.substring(0, TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX) + "…h"
				+ Integer.toHexString(di.hashCode());
	}

	private static String buildTransportAttemptMethodLabel(TransportFst transport)
	{
		String prefix = transport.getType() + ":";
		String di = transport.getDisplayInfo();
		if (di == null || di.isEmpty())
		{
			return prefix;
		}
		if (di.length() <= TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX)
		{
			return prefix + di;
		}
		return prefix + di.substring(0, TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX) + "…h"
				+ Integer.toHexString(di.hashCode());
	}
}
