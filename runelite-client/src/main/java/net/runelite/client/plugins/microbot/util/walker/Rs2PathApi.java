package net.runelite.client.plugins.microbot.util.walker;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.*;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathEdge;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathTerminationReason;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionView;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Microbot-owned facade over the shortest-path plugin's mutable static state.
 *
 * <p><b>Why this exists (Stage 2 of the facade migration — see
 * {@code shortestpath/WEBWALKER_IMPROVEMENT_PLAN.md} "Facade migration").</b>
 * Automation code ({@code Rs2Walker} and ~25 other consumers) currently reaches directly into
 * {@link ShortestPathPlugin}'s public static fields and accessors. Every time an upstream
 * (Skretzo/shortest-path) fix touches that internal wiring, the walker is at risk. Routing all
 * plugin-state access through this single class freezes the surface the walker sees, so future
 * upstream backports can change the plugin internals while only this facade (and not every
 * consumer) has to move with them.</p>
 *
 * <p><b>Contract.</b> This is a <i>thin, 1:1 delegation</i>. Every method here forwards verbatim to
 * the corresponding {@link ShortestPathPlugin} static member catalogued in the Stage 1 sweep. It
 * intentionally introduces <b>no behaviour change</b> and holds <b>no state</b> of its own. The
 * value types it returns ({@link Pathfinder}, {@link PathfinderConfig}, {@link Transport},
 * {@code TransportType}, {@code WorldPointUtil}) are treated as the stable Microbot-facing path API
 * and are deliberately <i>not</i> re-wrapped — they are pure data / pure functions.</p>
 *
 * <p><b>Migration status.</b> Stage 3 is complete: every consumer under {@code microbot/util/} now
 * routes through this facade, so the only remaining references to {@link ShortestPathPlugin}'s
 * static members outside the {@code shortestpath} package are the delegations below. That invariant
 * is greppable, and is what keeps the blast radius of an upstream backport confined to this class:
 * <pre>grep -rn "ShortestPathPlugin\." microbot/util/   # expect hits in Rs2PathApi only</pre>
 * {@link ShortestPathPlugin}'s members remain public and binary-compatible for out-of-tree callers.
 * Do not add logic here — if a call needs new behaviour, put it behind the plugin and expose it
 * through a matching delegate.</p>
 */
public final class Rs2PathApi
{

	private static final class LocalRoutePlanner implements Rs2RoutePlanner
	{
		private final PathfinderConfig config;

		private LocalRoutePlanner(PathfinderConfig config)
		{
			this.config = Objects.requireNonNull(config, "config");
		}

		@Override
		public String getEngineId()
		{
			return "microbot-local";
		}

		@Override
		public Rs2RouteResult plan(Rs2RouteRequest request, Rs2PlanningSnapshot snapshot)
		{
			Objects.requireNonNull(request, "request");
			Objects.requireNonNull(snapshot, "snapshot");
			Rs2RoutePolicy policy = request.getPolicy().orElseThrow(
					() -> new IllegalArgumentException("route planner requires a resolved policy"));
			if (snapshot.getPolicy() != policy)
			{
				throw new IllegalArgumentException("route request and planning snapshot policy differ");
			}
			long started = System.nanoTime();
			Pathfinder pathfinder = new Pathfinder(
					config, request.getStart(), request.getTargets());
			pathfinder.run();
			long elapsed = System.nanoTime() - started;
			return snapshot(pathfinder, elapsed);
		}
	}

	private static final class CatalogEdgeSnapshot
	{
		private final Set<Transport> source;
		private final List<Rs2TransportEdge> edges;

		private CatalogEdgeSnapshot(Set<Transport> source, List<Rs2TransportEdge> edges)
		{
			this.source = source;
			this.edges = edges;
		}
	}

	private static final class PlannerComparisonTicket
	{
		private final long generation;
		private final Rs2PlannerShadowContext context;

		private PlannerComparisonTicket(long generation, Rs2PlannerShadowContext context)
		{
			this.generation = generation;
			this.context = context;
		}
	}

	private static final class PlannerEvaluation
	{
		private final PlannerComparisonTicket ticket;
		private final Rs2RouteResult local;
		private final Rs2RouteResult candidate;
		private final Rs2PlannerShadowComparison comparison;

		private PlannerEvaluation(
				PlannerComparisonTicket ticket,
				Rs2RouteResult local,
				Rs2RouteResult candidate,
				Rs2PlannerShadowComparison comparison)
		{
			this.ticket = ticket;
			this.local = local;
			this.candidate = candidate;
			this.comparison = comparison;
		}

		private PlannerEvaluation materializationFailed(RuntimeException failure)
		{
			return new PlannerEvaluation(
					ticket,
					local,
					null,
					Rs2PlannerShadowComparison.failed(
							comparison.getShadowEngineId(), ticket.context,
							local, failure));
		}
	}

	private Rs2PathApi()
	{
	}

	private static long shadowGeneration;
	private static final Object shadowEvidenceMutex = new Object();
	private static long shadowSubmitted;
	private static volatile Rs2PlannerShadowComparison lastShadowComparison;
	private static volatile Rs2PlannerShadowComparison lastRouteShapeDifference;
	private static volatile Rs2PlannerShadowComparison lastDivergence;
	private static volatile Rs2PlannerShadowComparison lastPlannerFailure;
	private static long shadowDiscarded;
	private static long shadowCompleted;
	private static long shadowMatches;
	private static long shadowDivergences;
	private static long shadowFailures;
	private static long shadowStaleResults;
	private static long shadowRouteShapeDifferences;
	private static final long[][] shadowCoverageOutcomes = new long
			[Rs2PlannerShadowContext.Coverage.values().length]
			[Rs2PlannerShadowComparison.Status.values().length];
	private static final long[][] shadowTransportExecutorOutcomes = new long
			[Rs2TransportExecutor.values().length]
			[Rs2PlannerShadowComparison.Status.values().length];
	private static final long[][] shadowTransportTypeOutcomes = new long
			[Rs2TransportType.values().length]
			[Rs2PlannerShadowComparison.Status.values().length];
	private static long upstreamCanarySelections;
	private static long localFallbackDivergences;
	private static long localFallbackFailures;
	private static long shadowWalkerArrivals;
	private static long shadowWalkerUnreachable;
	private static long shadowWalkerExits;
	private static long shadowRecoveryArrivals;
	private static long shadowRecoveryUnreachable;
	private static long shadowRecoveryExits;
	private static long canaryPlanningSamples;
	private static long canaryPlanningNanosTotal;
	private static long canaryPlanningNanosMax;
	private static long canaryLocalSearchNanosTotal;
	private static long canaryLocalSearchNanosMax;
	private static long canaryUpstreamSearchSamples;
	private static long canaryUpstreamSearchNanosTotal;
	private static long canaryUpstreamSearchNanosMax;
	private static final long shadowEvidenceStartedAtEpochMillis = System.currentTimeMillis();
	private static final AtomicLong activeRouteGeneration = new AtomicLong();


	private static final ThreadPoolExecutor SHADOW_EXECUTOR = new ThreadPoolExecutor(
			1, 1, 0L, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(1),
			new ThreadFactoryBuilder().setDaemon(true)
					.setNameFormat("microbot-upstream-planner-shadow-%d").build(),
			(command, executor) ->
			{
				if (executor.isShutdown())
				{
					recordShadowDiscarded();
					return;
				}
				Runnable discarded = executor.getQueue().poll();
				if (discarded != null)
				{
					recordShadowDiscarded();
				}
				if (!executor.getQueue().offer(command))
				{
					recordShadowDiscarded();
				}
			});

	/** Config group key for the shortest-path plugin ({@link ShortestPathPlugin#CONFIG_GROUP}). */
	public static final String CONFIG_GROUP = ShortestPathPlugin.CONFIG_GROUP;

	/** Shared world-map marker sprite ({@link ShortestPathPlugin#MARKER_IMAGE}). */
	public static final BufferedImage MARKER_IMAGE = ShortestPathPlugin.MARKER_IMAGE;

	// ------------------------------------------------------------------
	// Pathfinder lifecycle
	// ------------------------------------------------------------------

	/** @return the current pathfinder instance, or {@code null} if none is running. */
	public static Pathfinder getPathfinder()
	{
		return ShortestPathPlugin.getPathfinder();
	}

	public static void setPathfinder(Pathfinder pathfinder)
	{
		ShortestPathPlugin.setPathfinder(pathfinder);
	}

	/** @return the {@link Future} tracking the in-flight pathfinding task, or {@code null}. */
	public static Future<?> getPathfinderFuture()
	{
		return ShortestPathPlugin.getPathfinderFuture();
	}

	public static void setPathfinderFuture(Future<?> future)
	{
		ShortestPathPlugin.setPathfinderFuture(future);
	}

	/** @return the single-threaded executor pathfinding runs on. */
	public static ExecutorService getPathfindingExecutor()
	{
		return ShortestPathPlugin.getPathfindingExecutor();
	}

	public static void setPathfindingExecutor(ExecutorService executor)
	{
		ShortestPathPlugin.setPathfindingExecutor(executor);
	}

	/** @return the monitor guarding pathfinder start/cancel transitions. */
	public static Object getPathfinderMutex()
	{
		return ShortestPathPlugin.getPathfinderMutex();
	}

	// ------------------------------------------------------------------
	// Config
	// ------------------------------------------------------------------

	/** @return the shared pathfinder configuration (transports, restrictions, toggles). */
	public static PathfinderConfig getPathfinderConfig()
	{
		return ShortestPathPlugin.getPathfinderConfig();
	}

	/** Distance from the target at which the path is considered reached. */
	public static void setReachedDistance(int reachedDistance)
	{
		ShortestPathPlugin.setReachedDistance(reachedDistance);
	}

	public static boolean override(String configOverrideKey, boolean defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}

	public static int override(String configOverrideKey, int defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}

	public static TeleportationItem override(String configOverrideKey, TeleportationItem defaultValue)
	{
		return ShortestPathPlugin.override(configOverrideKey, defaultValue);
	}

	// ------------------------------------------------------------------
	// Target / walker state
	// ------------------------------------------------------------------

	/** Clears the active target and tears down the current path (see {@link ShortestPathPlugin#exit()}). */
	public static void exit()
	{
		ShortestPathPlugin.exit();
	}

	public static boolean isStartPointSet()
	{
		return ShortestPathPlugin.isStartPointSet();
	}

	public static void setStartPointSet(boolean startPointSet)
	{
		ShortestPathPlugin.setStartPointSet(startPointSet);
	}

	/** Records the player's last known world location (write-only on the plugin). */
	public static void setLastLocation(WorldPoint lastLocation)
	{
		ShortestPathPlugin.setLastLocation(lastLocation);
	}

	// ------------------------------------------------------------------
	// Marker (world-map overlay)
	// ------------------------------------------------------------------

	public static WorldMapPoint getMarker()
	{
		return ShortestPathPlugin.getMarker();
	}

	public static void setMarker(WorldMapPoint marker)
	{
		ShortestPathPlugin.setMarker(marker);
	}

	// ------------------------------------------------------------------
	// Transport data
	// ------------------------------------------------------------------

	/** @return the transport graph keyed by origin tile. */
	public static Map<WorldPoint, Set<Transport>> getTransports()
	{
		return ShortestPathPlugin.getTransports();
	}

	/**
	 * Exact local execution selection for the runtime walker.
	 *
	 * <p>The immutable edge and its executor are the engine-independent contract. The local transport is
	 * an opaque implementation payload for the existing handlers (not a public planning value); POH in
	 * particular carries executable subtype behavior. It is always the exact object selected by search,
	 * never a catalog lookup or origin/destination rematch.</p>
	 */
	static final class ActiveTransportSelection
	{
		private final int pathIndex;
		private final Rs2TransportEdge edge;
		private final Transport localExecutionTransport;

		private ActiveTransportSelection(int pathIndex, Rs2TransportEdge edge, Transport localExecutionTransport)
		{
			this.pathIndex = pathIndex;
			this.edge = edge;
			this.localExecutionTransport = localExecutionTransport;
		}

		int getPathIndex() { return pathIndex; }
		Rs2TransportEdge getEdge() { return edge; }
		Rs2TransportExecutor getExecutor() { return edge.getExecutor(); }
		Transport getLocalExecutionTransport() { return localExecutionTransport; }
		boolean isExecutable() { return getExecutor() != Rs2TransportExecutor.UNSUPPORTED; }
	}

	/**
	 * Calculate a route without publishing it as the active walker pathfinder.
	 *
	 * <p>The shared configuration and its transport snapshots are mutable, so synchronous searches are
	 * serialized with walker start/cancel transitions. A request-level bank-item policy is temporary and
	 * always restored, including when refresh or pathfinding fails. This operation must run on a script or
	 * worker thread because refresh and search are blocking.</p>
	 */
	public static Rs2RouteResult plan(Rs2RouteRequest request)
	{
		return calculate(request);
	}

	private static void recordShadowDiscarded()
	{
		synchronized (shadowEvidenceMutex)
		{
			shadowDiscarded++;
		}
	}

	private static Rs2RouteResult calculate(Rs2RouteRequest request)
	{
		Objects.requireNonNull(request, "request");
		if (Microbot.getClientThread() != null && Microbot.getClientThread().isClientThread())
		{
			throw new IllegalStateException("synchronous route planning must not run on the client thread");
		}
		PathfinderConfig config = getPathfinderConfig();
		if (config == null)
		{
			throw new IllegalStateException("shortest-path configuration is not initialized");
		}

		synchronized (getPathfinderMutex())
		{
			Boolean requestedBankItems = request.getUseBankItems();
			boolean originalUseBankItems = config.isUseBankItems();
			boolean bankPolicyChanged = requestedBankItems != null
					&& requestedBankItems != originalUseBankItems;
			try
			{
				if (bankPolicyChanged)
				{
					config.setUseBankItems(requestedBankItems);
				}
				if (shouldRefresh(request, config))
				{
					config.refresh(request.getRefreshTarget());
				}
				Rs2RouteRequest resolved = resolvePolicy(request, config);
				Rs2PlanningSnapshot snapshot = resolvePlanningSnapshot(resolved, config);
				long canaryPlanningStarted = System.nanoTime();
				Rs2RouteResult local = localPlanner(config).plan(resolved, snapshot);
				PlannerSelectionMode plannerMode = config.getPlannerSelectionMode();
				if (isF2pCanary(plannerMode, resolved))
				{
					PlannerEvaluation evaluation = evaluateUpstream(
							resolved,
							snapshot,
							local,
							Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY,
							false);
					recordPlannerComparison(evaluation);
					recordCanaryOutcome(
							evaluation.comparison,
							elapsedNanos(canaryPlanningStarted),
							evaluation.comparison.getLocalSearchNanos());
					return shouldSelectUpstream(evaluation.comparison)
							? evaluation.candidate : local;
				}
				if (plannerMode == PlannerSelectionMode.SHADOW)
				{
					submitUpstreamShadow(
							resolved,
							snapshot,
							local,
							Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY,
							false);
				}
				return local;
			}
			finally
			{
				if (bankPolicyChanged)
				{
					config.setUseBankItems(originalUseBankItems);
					config.refresh(request.getRefreshTarget());
				}
			}
		}
	}

	/**
	 * Capture a coherent immutable view of the currently published route.
	 *
	 * <p>The lifecycle mutex prevents a route replacement halfway through the copy. The pathfinder may
	 * continue improving its partial path while calculating, but the returned lists cannot change under
	 * the caller.</p>
	 */
	public static Rs2ActiveRouteStatus getActiveRouteStatus()
	{
		synchronized (getPathfinderMutex())
		{
			long generation = activeRouteGeneration.get();
			Pathfinder source = getPathfinder();
			if (source == null)
			{
				return Rs2ActiveRouteStatus.absent(generation);
			}

			Future<?> activeFuture = getPathfinderFuture();
			boolean selectionComplete = activeFuture == null || activeFuture.isDone();
			boolean ready = source.isDone() && selectionComplete;
			List<WorldPoint> rawPath = immutablePath(source.getPath());
			List<WorldPoint> walkablePath = ready
					? immutablePath(source.getWalkablePath())
					: rawPath;
			if (!ready)
			{
				return new Rs2ActiveRouteStatus(
						generation,
						Rs2ActiveRouteStatus.Phase.CALCULATING,
						source.getStart(),
						source.getTargets(),
						rawPath,
						walkablePath,
						null,
						null);
			}

			Pathfinder.PathfinderStats stats = source.getStats();
			Rs2RouteMetrics metrics = new Rs2RouteMetrics(
					stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getElapsedTimeNanos(),
					source.getSelectedPathCost(),
					stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getNodesChecked(),
					stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getTransportsChecked());
			return new Rs2ActiveRouteStatus(
					generation,
					Rs2ActiveRouteStatus.Phase.READY,
					source.getStart(),
					source.getTargets(),
					rawPath,
					walkablePath,
					mapTermination(source.getTerminationReason()),
					metrics);
		}
	}

	/** Invalidate cached transport-policy snapshots so the next refresh observes external state. */
	public static boolean invalidateTransportRefreshCache()
	{
		PathfinderConfig config = getPathfinderConfig();
		if (config == null)
		{
			return false;
		}
		synchronized (getPathfinderMutex())
		{
			config.invalidateTransportRefreshCache();
		}
		return true;
	}

	private static List<WorldPoint> immutablePath(List<WorldPoint> path)
	{
		return path == null || path.isEmpty() ? Collections.emptyList() : List.copyOf(path);
	}

	private static Rs2RouteResult snapshot(Pathfinder pathfinder, long elapsed)
	{
		Pathfinder.PathfinderStats stats = pathfinder.getStats();
		List<WorldPoint> path = pathfinder.getPath() == null
				? Collections.emptyList()
				: pathfinder.getPath();
		List<Rs2RouteStep> steps = new ArrayList<>();
		for (PathEdge edge : pathfinder.getPathEdges())
		{
			if (edge.isTransport())
			{
				steps.add(Rs2RouteStep.transport(
						edge.getFrom(), edge.getTo(), toTransportEdge(edge.getTransport())));
			}
			else
			{
				steps.add(Rs2RouteStep.walk(edge.getFrom(), edge.getTo()));
			}
		}
		return new Rs2RouteResult(
				pathfinder.getStart(),
				pathfinder.getTargets(),
				path,
				steps,
				mapTermination(pathfinder.getTerminationReason()),
				new Rs2RouteMetrics(
						elapsed,
						pathfinder.getSelectedPathCost(),
						stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getNodesChecked(),
						stats == null ? Rs2RouteMetrics.UNAVAILABLE : stats.getTransportsChecked(),
						stats == null ? Rs2RouteMetrics.UNAVAILABLE
								: stats.getLiveCollisionEdgesChecked()));
	}

	private static Rs2RouteTermination mapTermination(PathTerminationReason reason)
	{
		if (reason == null)
		{
			return Rs2RouteTermination.FAILED;
		}
		switch (reason)
		{
			case TARGET_REACHED:
				return Rs2RouteTermination.TARGET_REACHED;
			case SEARCH_EXHAUSTED:
				return Rs2RouteTermination.SEARCH_EXHAUSTED;
			case CUTOFF_REACHED:
				return Rs2RouteTermination.CUTOFF_REACHED;
			case CANCELLED:
				return Rs2RouteTermination.CANCELLED;
			case FAILED:
			default:
				return Rs2RouteTermination.FAILED;
		}
	}

	/** Process-lifetime counters for bounded, non-authoritative shadow evidence. */
	public static Rs2PlannerShadowStats getShadowStats()
	{
		synchronized (shadowEvidenceMutex)
		{
			EnumMap<Rs2PlannerShadowContext.Coverage, Rs2PlannerShadowCoverageStats>
					coverage = new EnumMap<>(Rs2PlannerShadowContext.Coverage.class);
			for (Rs2PlannerShadowContext.Coverage value
					: Rs2PlannerShadowContext.Coverage.values())
			{
				long[] outcomes = shadowCoverageOutcomes[value.ordinal()];
				coverage.put(value, new Rs2PlannerShadowCoverageStats(
						outcomes[Rs2PlannerShadowComparison.Status.MATCH.ordinal()],
						outcomes[Rs2PlannerShadowComparison.Status.DIVERGENCE.ordinal()],
						outcomes[Rs2PlannerShadowComparison.Status.FAILED.ordinal()]));
			}
			EnumMap<Rs2TransportExecutor, Rs2PlannerShadowCoverageStats>
					transportExecutors = new EnumMap<>(Rs2TransportExecutor.class);
			for (Rs2TransportExecutor value : Rs2TransportExecutor.values())
			{
				long[] outcomes = shadowTransportExecutorOutcomes[value.ordinal()];
				transportExecutors.put(value, new Rs2PlannerShadowCoverageStats(
						outcomes[Rs2PlannerShadowComparison.Status.MATCH.ordinal()],
						outcomes[Rs2PlannerShadowComparison.Status.DIVERGENCE.ordinal()],
						outcomes[Rs2PlannerShadowComparison.Status.FAILED.ordinal()]));
			}
			EnumMap<Rs2TransportType, Rs2PlannerShadowCoverageStats>
					transportTypes = new EnumMap<>(Rs2TransportType.class);
			for (Rs2TransportType value : Rs2TransportType.values())
			{
				long[] outcomes = shadowTransportTypeOutcomes[value.ordinal()];
				transportTypes.put(value, new Rs2PlannerShadowCoverageStats(
						outcomes[Rs2PlannerShadowComparison.Status.MATCH.ordinal()],
						outcomes[Rs2PlannerShadowComparison.Status.DIVERGENCE.ordinal()],
						outcomes[Rs2PlannerShadowComparison.Status.FAILED.ordinal()]));
			}
			return new Rs2PlannerShadowStats(
					shadowSubmitted,
					shadowCompleted,
					shadowMatches,
					shadowDivergences,
					shadowFailures,
					shadowStaleResults,
					shadowDiscarded,
					shadowRouteShapeDifferences,
					upstreamCanarySelections,
					localFallbackDivergences,
					localFallbackFailures,
					shadowEvidenceStartedAtEpochMillis,
					coverage,
					transportExecutors,
					transportTypes,
					new Rs2WalkerShadowExecutionStats(
							shadowWalkerArrivals,
							shadowWalkerUnreachable,
							shadowWalkerExits,
							shadowRecoveryArrivals,
							shadowRecoveryUnreachable,
							shadowRecoveryExits),
					new Rs2PlannerCanaryPerformanceStats(
							canaryPlanningSamples,
							canaryPlanningNanosTotal,
							canaryPlanningNanosMax,
							canaryLocalSearchNanosTotal,
							canaryLocalSearchNanosMax,
							canaryUpstreamSearchSamples,
							canaryUpstreamSearchNanosTotal,
							canaryUpstreamSearchNanosMax));
		}
	}

	private static PathTerminationReason mapTermination(Rs2RouteTermination reason)
	{
		if (reason == null)
		{
			return PathTerminationReason.FAILED;
		}
		switch (reason)
		{
			case TARGET_REACHED:
				return PathTerminationReason.TARGET_REACHED;
			case SEARCH_EXHAUSTED:
				return PathTerminationReason.SEARCH_EXHAUSTED;
			case CUTOFF_REACHED:
				return PathTerminationReason.CUTOFF_REACHED;
			case CANCELLED:
				return PathTerminationReason.CANCELLED;
			case FAILED:
			default:
				return PathTerminationReason.FAILED;
		}
	}

	static Rs2RoutePlanner localPlanner(PathfinderConfig config)
	{
		return new LocalRoutePlanner(config);
	}


	static boolean isF2pCanary(
			PlannerSelectionMode plannerMode, Rs2RouteRequest request)
	{
		return plannerMode != null
				&& plannerMode.f2pCanaryEnabled()
				&& request != null
				&& request.getPolicy().map(policy -> !policy.isMembersWorld()).orElse(false);
	}

	private static PlannerEvaluation evaluateUpstream(
			Rs2RouteRequest request,
			Rs2PlanningSnapshot snapshot,
			Rs2RouteResult local,
			Rs2PlannerShadowContext.Invocation invocation,
			boolean walkingOnlySelected)
	{
		Rs2PlannerShadowContext context = Rs2PlannerShadowContext.from(
				invocation, walkingOnlySelected, request, local);
		return evaluateUpstream(beginPlannerComparison(context), request, snapshot, local);
	}

	static void recordCanaryOutcome(
			Rs2PlannerShadowComparison comparison,
			long planningNanos,
			long localPlanningNanos)
	{
		synchronized (shadowEvidenceMutex)
		{
			if (planningNanos < 0L || localPlanningNanos < 0L)
			{
				throw new IllegalArgumentException(
						"canary planning and local search durations must be available");
			}
			switch (comparison.getStatus())
			{
				case MATCH: upstreamCanarySelections++; break;
				case DIVERGENCE: localFallbackDivergences++; break;
				case FAILED: localFallbackFailures++; break;
				default: throw new IllegalStateException(
						"unhandled canary comparison status " + comparison.getStatus());
			}
			canaryPlanningSamples++;
			canaryPlanningNanosTotal = saturatedAdd(
					canaryPlanningNanosTotal, planningNanos);
			canaryPlanningNanosMax = Math.max(canaryPlanningNanosMax, planningNanos);
			canaryLocalSearchNanosTotal = saturatedAdd(
					canaryLocalSearchNanosTotal, localPlanningNanos);
			canaryLocalSearchNanosMax = Math.max(
					canaryLocalSearchNanosMax, localPlanningNanos);
			long upstreamSearchNanos = comparison.getShadowSearchNanos();
			if (upstreamSearchNanos >= 0L)
			{
				canaryUpstreamSearchSamples++;
				canaryUpstreamSearchNanosTotal = saturatedAdd(
						canaryUpstreamSearchNanosTotal, upstreamSearchNanos);
				canaryUpstreamSearchNanosMax = Math.max(
						canaryUpstreamSearchNanosMax, upstreamSearchNanos);
			}
		}
	}

	private static long saturatedAdd(long left, long right)
	{
		if (right > Long.MAX_VALUE - left)
		{
			return Long.MAX_VALUE;
		}
		return left + right;
	}

	private static long elapsedNanos(long started)
	{
		return Math.max(0L, System.nanoTime() - started);
	}

	/** A route-shape-only difference is a semantic match and remains eligible for the canary. */
	static boolean shouldSelectUpstream(Rs2PlannerShadowComparison comparison)
	{
		return comparison != null
				&& comparison.getStatus() == Rs2PlannerShadowComparison.Status.MATCH;
	}

	static Rs2PlanningSnapshot resolvePlanningSnapshot(
			Rs2RouteRequest request, PathfinderConfig config)
	{
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(config, "config");
		Rs2RoutePolicy policy = request.getPolicy().orElseThrow(
				() -> new IllegalArgumentException("planning snapshot requires a resolved policy"));
		Set<Transport> admitted = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		Map<WorldPoint, Set<Transport>> activeTransports = config.getTransports();
		if (activeTransports != null)
		{
			for (Set<Transport> values : activeTransports.values())
			{
				admitted.addAll(values);
			}
		}
		Set<Transport> usableTeleports = config.getUsableTeleportsSnapshot();
		if (usableTeleports != null)
		{
			admitted.addAll(usableTeleports);
		}
		List<Rs2TransportEdge> edges = new ArrayList<>(admitted.size());
		for (Transport transport : admitted)
		{
			edges.add(toTransportEdge(transport));
		}
		net.runelite.client.plugins.microbot.shortestpath.pathfinder.live.LiveCollisionOverlay overlay =
				config.getLiveCollisionOverlay();
		LiveCollisionView live = overlay == null ? null : overlay.current();
		Rs2PlanningSnapshot.CollisionOverride collision = live == null ? null : live::edge;
		Set<Long> blocked = config.getBlockedTransportEdgesPacked();
		return new Rs2PlanningSnapshot(
				policy,
				edges,
				collision,
				blocked == null ? Collections.emptySet() : new LinkedHashSet<>(blocked),
				config::isDangerousAdjacentTile);
	}

	private static void submitUpstreamShadow(
			Rs2RouteRequest request,
			Rs2PlanningSnapshot snapshot,
			Rs2RouteResult local,
			Rs2PlannerShadowContext.Invocation invocation,
			boolean walkingOnlySelected)
	{
		Rs2PlannerShadowContext context = Rs2PlannerShadowContext.from(
				invocation, walkingOnlySelected, request, local);
		PlannerComparisonTicket ticket = beginPlannerComparison(context);
		SHADOW_EXECUTOR.execute(() -> recordPlannerComparison(
				evaluateUpstream(ticket, request, snapshot, local)));
	}

	private static PlannerEvaluation evaluateUpstream(
			PlannerComparisonTicket ticket,
			Rs2RouteRequest request,
			Rs2PlanningSnapshot snapshot,
			Rs2RouteResult local)
	{
		Rs2RoutePlanner planner = upstreamPlanner();
		try
		{
			Rs2RouteResult candidate = planner.plan(request, snapshot);
			return new PlannerEvaluation(
					ticket,
					local,
					candidate,
					Rs2PlannerShadowComparison.compare(
							planner.getEngineId(), ticket.context, local, candidate));
		}
		catch (RuntimeException failure)
		{
			return new PlannerEvaluation(
					ticket,
					local,
					null,
					Rs2PlannerShadowComparison.failed(
							planner.getEngineId(), ticket.context, local, failure));
		}
	}

	static Rs2RoutePlanner upstreamPlanner()
	{
		UpstreamRoutePlanner delegate = new UpstreamRoutePlanner();
		if (Boolean.getBoolean("microbot.test.mode")
				&& Boolean.getBoolean("microbot.test.walker.forceUpstreamPlannerFailure"))
		{
			return new Rs2RoutePlanner()
			{
				@Override
				public String getEngineId()
				{
					return delegate.getEngineId();
				}

				@Override
				public Rs2RouteResult plan(
						Rs2RouteRequest request, Rs2PlanningSnapshot snapshot)
				{
					throw new IllegalStateException("synthetic upstream rollout failure");
				}
			};
		}
		return delegate;
	}

	private static void recordPlannerComparison(PlannerEvaluation evaluation)
	{
		Rs2PlannerShadowComparison comparison = evaluation.comparison;
		synchronized (shadowEvidenceMutex)
		{
			switch (comparison.getStatus())
			{
				case MATCH: shadowMatches++; break;
				case DIVERGENCE:
					shadowDivergences++;
					lastDivergence = comparison;
					break;
				case FAILED:
					shadowFailures++;
					lastPlannerFailure = comparison;
					break;
				default: throw new IllegalStateException(
						"unhandled shadow comparison status " + comparison.getStatus());
			}
			for (Rs2PlannerShadowContext.Coverage value
					: comparison.getContext().getCoverage())
			{
				shadowCoverageOutcomes[value.ordinal()][comparison.getStatus().ordinal()]++;
			}
			for (Rs2TransportExecutor value
					: comparison.getContext().getTransportExecutors())
			{
				shadowTransportExecutorOutcomes[value.ordinal()]
						[comparison.getStatus().ordinal()]++;
			}
			for (Rs2TransportType value : comparison.getContext().getTransportTypes())
			{
				shadowTransportTypeOutcomes[value.ordinal()]
						[comparison.getStatus().ordinal()]++;
			}
			if (comparison.getStatus() != Rs2PlannerShadowComparison.Status.FAILED
					&& !comparison.isPathMatches())
			{
				shadowRouteShapeDifferences++;
				lastRouteShapeDifference = comparison;
			}
			shadowCompleted++;
			if (shadowGeneration == evaluation.ticket.generation)
			{
				lastShadowComparison = comparison;
			}
			else
			{
				shadowStaleResults++;
			}
		}
	}

	private static PlannerComparisonTicket beginPlannerComparison(
			Rs2PlannerShadowContext context)
	{
		synchronized (shadowEvidenceMutex)
		{
			long generation = ++shadowGeneration;
			shadowSubmitted++;
			lastShadowComparison = null;
			return new PlannerComparisonTicket(generation, context);
		}
	}

	private static Rs2TransportEdge toTransportEdge(Transport transport)
	{
		List<Rs2TransportItemRequirement> itemRequirements = new ArrayList<>();
		for (TransportItemRequirement requirement : transport.getItemRequirements())
		{
			itemRequirements.add(new Rs2TransportItemRequirement(
					requirement.getAlternatives(),
					requirement.getStaffAlternatives(),
					requirement.getOffhandAlternatives(),
					requirement.isRuneOnly()));
		}
		return new Rs2TransportEdge(
				transport.getOrigin(),
				transport.getDestination(),
				mapTransportType(transport.getType()),
				mapExecutor(TransportExecutionRegistry.executorFor(transport).orElse(null)),
				mapTerminalTravelMode(transport),
				transport.getDisplayInfo(),
				transport.getAction(),
				transport.getName(),
				transport.getObjectId(),
				transport.getDuration(),
				TransportType.isTeleport(transport.getType(), transport.getOrigin()),
				transport.isConsumable(),
				transport.isMembers(),
				transport.getMaxWildernessLevel(),
				transport.getCurrencyName(),
				transport.getCurrencyAmount(),
				itemRequirements,
				Arrays.stream(transport.getSkillLevels()).anyMatch(level -> level > 0),
				transport.isQuestLocked(),
				!transport.getVarbits().isEmpty() || !transport.getVarplayers().isEmpty(),
				transport);
	}

	private static Rs2TransportExecutor mapExecutor(TransportExecutionRegistry.Executor executor)
	{
		if (executor == null)
		{
			return Rs2TransportExecutor.UNSUPPORTED;
		}
		try
		{
			return Rs2TransportExecutor.valueOf(executor.name());
		}
		catch (IllegalArgumentException ignored)
		{
			return Rs2TransportExecutor.UNSUPPORTED;
		}
	}

	private static Rs2TerminalTravelMode mapTerminalTravelMode(Transport transport)
	{
		return TransportExecutionRegistry.terminalTravelModeFor(transport)
				.map(mode -> Rs2TerminalTravelMode.valueOf(mode.name()))
				.orElse(Rs2TerminalTravelMode.UNSUPPORTED);
	}

	static Rs2RouteRequest resolvePolicy(
			Rs2RouteRequest request, PathfinderConfig config)
	{
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(config, "config");
		EnumSet<Rs2TransportType> enabledTypes = EnumSet.noneOf(Rs2TransportType.class);
		for (TransportType type : config.getEnabledTransportTypes())
		{
			enabledTypes.add(mapTransportType(type));
		}
		Set<WorldPoint> restrictedPoints = new LinkedHashSet<>();
		for (int packed : config.getRestrictedPointsPacked())
		{
			restrictedPoints.add(WorldPointUtil.unpackWorldPoint(packed));
		}
		Rs2RoutePolicy policy = new Rs2RoutePolicy(
				config.isUseBankItems(),
				config.isAvoidWilderness(),
				config.isAvoidDangerousNpcs(),
				config.isIgnoreTeleportAndItems(),
				Rs2Walker.disableTeleports,
				config.isMembersWorld(),
				config.getLiveCollisionOverlay().isEnabled(),
				config.getCalculationCutoffMillis(),
				config.getDistanceBeforeUsingTeleport(),
				Rs2RoutePolicy.TeleportationItemMode.valueOf(
						config.getTeleportationItemPolicy().name()),
				enabledTypes,
				restrictedPoints);
		return request.withPolicy(policy);
	}

	private static Rs2TransportType mapTransportType(TransportType type)
	{
		if (type == null)
		{
			return Rs2TransportType.UNKNOWN;
		}
		try
		{
			return Rs2TransportType.valueOf(type.name());
		}
		catch (IllegalArgumentException ignored)
		{
			return Rs2TransportType.UNKNOWN;
		}
	}

	private static boolean shouldRefresh(Rs2RouteRequest request, PathfinderConfig config)
	{
		if (request.getUseBankItems() != null
				|| request.getRefreshPolicy() == Rs2RouteRequest.RefreshPolicy.ALWAYS)
		{
			return true;
		}
		return request.getRefreshPolicy() == Rs2RouteRequest.RefreshPolicy.IF_TRANSPORTS_EMPTY
				&& config.getTransports().isEmpty();
	}

}
