package net.runelite.client.plugins.microbot.walkerfst;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.*;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.pathfinder.PathEdgeFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.pathfinder.PathTerminationReasonFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.pathfinder.PathfinderFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.pathfinder.PathfinderConfigFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.pathfinder.live.LiveCollisionViewFst;
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
public final class Rs2PathApiFst
{

	private static final class LocalRoutePlanner implements Rs2RoutePlannerFst
	{
		private final PathfinderConfigFst config;

		private LocalRoutePlanner(PathfinderConfigFst config)
		{
			this.config = Objects.requireNonNull(config, "config");
		}

		@Override
		public String getEngineId()
		{
			return "microbot-local";
		}

		@Override
		public Rs2RouteResultFst plan(Rs2RouteRequestFst request, Rs2PlanningSnapshotFst snapshot)
		{
			Objects.requireNonNull(request, "request");
			Objects.requireNonNull(snapshot, "snapshot");
			Rs2RoutePolicyFst policy = request.getPolicy().orElseThrow(
					() -> new IllegalArgumentException("route planner requires a resolved policy"));
			if (snapshot.getPolicy() != policy)
			{
				throw new IllegalArgumentException("route request and planning snapshot policy differ");
			}
			long started = System.nanoTime();
			PathfinderFst pathfinder = new PathfinderFst(
					config, request.getStart(), request.getTargets());
			pathfinder.run();
			long elapsed = System.nanoTime() - started;
			return snapshot(pathfinder, elapsed);
		}
	}

	private static final class CatalogEdgeSnapshot
	{
		private final Set<TransportFst> source;
		private final List<Rs2TransportEdgeFst> edges;

		private CatalogEdgeSnapshot(Set<TransportFst> source, List<Rs2TransportEdgeFst> edges)
		{
			this.source = source;
			this.edges = edges;
		}
	}

	private static final class PlannerComparisonTicket
	{
		private final long generation;
		private final Rs2PlannerShadowContextFst context;

		private PlannerComparisonTicket(long generation, Rs2PlannerShadowContextFst context)
		{
			this.generation = generation;
			this.context = context;
		}
	}

	private static final class PlannerEvaluation
	{
		private final PlannerComparisonTicket ticket;
		private final Rs2RouteResultFst local;
		private final Rs2RouteResultFst candidate;
		private final Rs2PlannerShadowComparisonFst comparison;

		private PlannerEvaluation(
				PlannerComparisonTicket ticket,
				Rs2RouteResultFst local,
				Rs2RouteResultFst candidate,
				Rs2PlannerShadowComparisonFst comparison)
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
					Rs2PlannerShadowComparisonFst.failed(
							comparison.getShadowEngineId(), ticket.context,
							local, failure));
		}
	}

	private Rs2PathApiFst()
	{
	}

	private static long shadowGeneration;
	private static final Object shadowEvidenceMutex = new Object();
	private static long shadowSubmitted;
	private static volatile Rs2PlannerShadowComparisonFst lastShadowComparison;
	private static volatile Rs2PlannerShadowComparisonFst lastRouteShapeDifference;
	private static volatile Rs2PlannerShadowComparisonFst lastDivergence;
	private static volatile Rs2PlannerShadowComparisonFst lastPlannerFailure;
	private static long shadowDiscarded;
	private static long shadowCompleted;
	private static long shadowMatches;
	private static long shadowDivergences;
	private static long shadowFailures;
	private static long shadowStaleResults;
	private static long shadowRouteShapeDifferences;
	private static final long[][] shadowCoverageOutcomes = new long
			[Rs2PlannerShadowContextFst.Coverage.values().length]
			[Rs2PlannerShadowComparisonFst.Status.values().length];
	private static final long[][] shadowTransportExecutorOutcomes = new long
			[Rs2TransportExecutorFst.values().length]
			[Rs2PlannerShadowComparisonFst.Status.values().length];
	private static final long[][] shadowTransportTypeOutcomes = new long
			[Rs2TransportTypeFst.values().length]
			[Rs2PlannerShadowComparisonFst.Status.values().length];
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
	public static final String CONFIG_GROUP = ShortestPathPluginFst.CONFIG_GROUP;

	/** Shared world-map marker sprite ({@link ShortestPathPlugin#MARKER_IMAGE}). */
	public static final BufferedImage MARKER_IMAGE = ShortestPathPluginFst.MARKER_IMAGE;

	// ------------------------------------------------------------------
	// Pathfinder lifecycle
	// ------------------------------------------------------------------

	/** @return the current pathfinder instance, or {@code null} if none is running. */
	public static PathfinderFst getPathfinder()
	{
		return ShortestPathPluginFst.getPathfinder();
	}

	public static void setPathfinder(PathfinderFst pathfinder)
	{
		ShortestPathPluginFst.setPathfinder(pathfinder);
	}

	/** @return the {@link Future} tracking the in-flight pathfinding task, or {@code null}. */
	public static Future<?> getPathfinderFuture()
	{
		return ShortestPathPluginFst.getPathfinderFuture();
	}

	public static void setPathfinderFuture(Future<?> future)
	{
		ShortestPathPluginFst.setPathfinderFuture(future);
	}

	/** @return the single-threaded executor pathfinding runs on. */
	public static ExecutorService getPathfindingExecutor()
	{
		return ShortestPathPluginFst.getPathfindingExecutor();
	}

	public static void setPathfindingExecutor(ExecutorService executor)
	{
		ShortestPathPluginFst.setPathfindingExecutor(executor);
	}

	/** @return the monitor guarding pathfinder start/cancel transitions. */
	public static Object getPathfinderMutex()
	{
		return ShortestPathPluginFst.getPathfinderMutex();
	}

	// ------------------------------------------------------------------
	// Config
	// ------------------------------------------------------------------

	/** @return the shared pathfinder configuration (transports, restrictions, toggles). */
	public static PathfinderConfigFst getPathfinderConfig()
	{
		return ShortestPathPluginFst.getPathfinderConfig();
	}

	/** Distance from the target at which the path is considered reached. */
	public static void setReachedDistance(int reachedDistance)
	{
		ShortestPathPluginFst.setReachedDistance(reachedDistance);
	}

	public static boolean override(String configOverrideKey, boolean defaultValue)
	{
		return ShortestPathPluginFst.override(configOverrideKey, defaultValue);
	}

	public static int override(String configOverrideKey, int defaultValue)
	{
		return ShortestPathPluginFst.override(configOverrideKey, defaultValue);
	}

	public static TeleportationItemFst override(String configOverrideKey, TeleportationItemFst defaultValue)
	{
		return ShortestPathPluginFst.override(configOverrideKey, defaultValue);
	}

	// ------------------------------------------------------------------
	// Target / walker state
	// ------------------------------------------------------------------

	/** Clears the active target and tears down the current path (see {@link ShortestPathPlugin#exit()}). */
	public static void exit()
	{
		ShortestPathPluginFst.exit();
	}

	public static boolean isStartPointSet()
	{
		return ShortestPathPluginFst.isStartPointSet();
	}

	public static void setStartPointSet(boolean startPointSet)
	{
		ShortestPathPluginFst.setStartPointSet(startPointSet);
	}

	/** Records the player's last known world location (write-only on the plugin). */
	public static void setLastLocation(WorldPoint lastLocation)
	{
		ShortestPathPluginFst.setLastLocation(lastLocation);
	}

	// ------------------------------------------------------------------
	// Marker (world-map overlay)
	// ------------------------------------------------------------------

	public static WorldMapPoint getMarker()
	{
		return ShortestPathPluginFst.getMarker();
	}

	public static void setMarker(WorldMapPoint marker)
	{
		ShortestPathPluginFst.setMarker(marker);
	}

	public static WorldMapPoint createTargetMarker(WorldPoint target)
	{
		WorldMapPoint marker = new WorldMapPoint(target, MARKER_IMAGE);
		marker.setName("Target");
		marker.setTarget(marker.getWorldPoint());
		marker.setJumpOnClick(true);
		return marker;
	}

	// ------------------------------------------------------------------
	// Transport data
	// ------------------------------------------------------------------

	/** @return the transport graph keyed by origin tile. */
	public static Map<WorldPoint, Set<TransportFst>> getTransports()
	{
		return ShortestPathPluginFst.getTransports();
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
		private final Rs2TransportEdgeFst edge;
		private final TransportFst localExecutionTransport;

		private ActiveTransportSelection(int pathIndex, Rs2TransportEdgeFst edge, TransportFst localExecutionTransport)
		{
			this.pathIndex = pathIndex;
			this.edge = edge;
			this.localExecutionTransport = localExecutionTransport;
		}

		int getPathIndex() { return pathIndex; }
		Rs2TransportEdgeFst getEdge() { return edge; }
		Rs2TransportExecutorFst getExecutor() { return edge.getExecutor(); }
		TransportFst getLocalExecutionTransport() { return localExecutionTransport; }
		boolean isExecutable() { return getExecutor() != Rs2TransportExecutorFst.UNSUPPORTED; }
	}

	/**
	 * Calculate a route without publishing it as the active walker pathfinder.
	 *
	 * <p>The shared configuration and its transport snapshots are mutable, so synchronous searches are
	 * serialized with walker start/cancel transitions. A request-level bank-item policy is temporary and
	 * always restored, including when refresh or pathfinding fails. This operation must run on a script or
	 * worker thread because refresh and search are blocking.</p>
	 */
	public static Rs2RouteResultFst plan(Rs2RouteRequestFst request)
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

	private static Rs2RouteResultFst calculate(Rs2RouteRequestFst request)
	{
		Objects.requireNonNull(request, "request");
		if (Microbot.getClientThread() != null && Microbot.getClientThread().isClientThread())
		{
			throw new IllegalStateException("synchronous route planning must not run on the client thread");
		}
		PathfinderConfigFst config = getPathfinderConfig();
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
				Rs2RouteRequestFst resolved = resolvePolicy(request, config);
				Rs2PlanningSnapshotFst snapshot = resolvePlanningSnapshot(resolved, config);
				long canaryPlanningStarted = System.nanoTime();
				Rs2RouteResultFst local = localPlanner(config).plan(resolved, snapshot);
				PlannerSelectionModeFst plannerMode = config.getPlannerSelectionMode();
				if (isF2pCanary(plannerMode, resolved))
				{
					PlannerEvaluation evaluation = evaluateUpstream(
							resolved,
							snapshot,
							local,
							Rs2PlannerShadowContextFst.Invocation.SYNCHRONOUS_QUERY,
							false);
					recordPlannerComparison(evaluation);
					recordCanaryOutcome(
							evaluation.comparison,
							elapsedNanos(canaryPlanningStarted),
							evaluation.comparison.getLocalSearchNanos());
					return shouldSelectUpstream(evaluation.comparison)
							? evaluation.candidate : local;
				}
				if (plannerMode == PlannerSelectionModeFst.SHADOW)
				{
					submitUpstreamShadow(
							resolved,
							snapshot,
							local,
							Rs2PlannerShadowContextFst.Invocation.SYNCHRONOUS_QUERY,
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
	public static Rs2ActiveRouteStatusFst getActiveRouteStatus()
	{
		synchronized (getPathfinderMutex())
		{
			long generation = activeRouteGeneration.get();
			PathfinderFst source = getPathfinder();
			if (source == null)
			{
				return Rs2ActiveRouteStatusFst.absent(generation);
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
				return new Rs2ActiveRouteStatusFst(
						generation,
						Rs2ActiveRouteStatusFst.Phase.CALCULATING,
						source.getStart(),
						source.getTargets(),
						rawPath,
						walkablePath,
						null,
						null);
			}

			PathfinderFst.PathfinderStats stats = source.getStats();
			Rs2RouteMetricsFst metrics = new Rs2RouteMetricsFst(
					stats == null ? Rs2RouteMetricsFst.UNAVAILABLE : stats.getElapsedTimeNanos(),
					source.getSelectedPathCost(),
					stats == null ? Rs2RouteMetricsFst.UNAVAILABLE : stats.getNodesChecked(),
					stats == null ? Rs2RouteMetricsFst.UNAVAILABLE : stats.getTransportsChecked());
			return new Rs2ActiveRouteStatusFst(
					generation,
					Rs2ActiveRouteStatusFst.Phase.READY,
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
		PathfinderConfigFst config = getPathfinderConfig();
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

	private static Rs2RouteResultFst snapshot(PathfinderFst pathfinder, long elapsed)
	{
		PathfinderFst.PathfinderStats stats = pathfinder.getStats();
		List<WorldPoint> path = pathfinder.getPath() == null
				? Collections.emptyList()
				: pathfinder.getPath();
		List<Rs2RouteStepFst> steps = new ArrayList<>();
		for (PathEdgeFst edge : pathfinder.getPathEdges())
		{
			if (edge.isTransport())
			{
				steps.add(Rs2RouteStepFst.transport(
						edge.getFrom(), edge.getTo(), toTransportEdge(edge.getTransport())));
			}
			else
			{
				steps.add(Rs2RouteStepFst.walk(edge.getFrom(), edge.getTo()));
			}
		}
		return new Rs2RouteResultFst(
				pathfinder.getStart(),
				pathfinder.getTargets(),
				path,
				steps,
				mapTermination(pathfinder.getTerminationReason()),
				new Rs2RouteMetricsFst(
						elapsed,
						pathfinder.getSelectedPathCost(),
						stats == null ? Rs2RouteMetricsFst.UNAVAILABLE : stats.getNodesChecked(),
						stats == null ? Rs2RouteMetricsFst.UNAVAILABLE : stats.getTransportsChecked(),
						stats == null ? Rs2RouteMetricsFst.UNAVAILABLE
								: stats.getLiveCollisionEdgesChecked()));
	}

	private static Rs2RouteTerminationFst mapTermination(PathTerminationReasonFst reason)
	{
		if (reason == null)
		{
			return Rs2RouteTerminationFst.FAILED;
		}
		switch (reason)
		{
			case TARGET_REACHED:
				return Rs2RouteTerminationFst.TARGET_REACHED;
			case SEARCH_EXHAUSTED:
				return Rs2RouteTerminationFst.SEARCH_EXHAUSTED;
			case CUTOFF_REACHED:
				return Rs2RouteTerminationFst.CUTOFF_REACHED;
			case CANCELLED:
				return Rs2RouteTerminationFst.CANCELLED;
			case FAILED:
			default:
				return Rs2RouteTerminationFst.FAILED;
		}
	}

	/** Process-lifetime counters for bounded, non-authoritative shadow evidence. */
	public static Rs2PlannerShadowStatsFst getShadowStats()
	{
		synchronized (shadowEvidenceMutex)
		{
			EnumMap<Rs2PlannerShadowContextFst.Coverage, Rs2PlannerShadowCoverageStatsFst>
					coverage = new EnumMap<>(Rs2PlannerShadowContextFst.Coverage.class);
			for (Rs2PlannerShadowContextFst.Coverage value
					: Rs2PlannerShadowContextFst.Coverage.values())
			{
				long[] outcomes = shadowCoverageOutcomes[value.ordinal()];
				coverage.put(value, new Rs2PlannerShadowCoverageStatsFst(
						outcomes[Rs2PlannerShadowComparisonFst.Status.MATCH.ordinal()],
						outcomes[Rs2PlannerShadowComparisonFst.Status.DIVERGENCE.ordinal()],
						outcomes[Rs2PlannerShadowComparisonFst.Status.FAILED.ordinal()]));
			}
			EnumMap<Rs2TransportExecutorFst, Rs2PlannerShadowCoverageStatsFst>
					transportExecutors = new EnumMap<>(Rs2TransportExecutorFst.class);
			for (Rs2TransportExecutorFst value : Rs2TransportExecutorFst.values())
			{
				long[] outcomes = shadowTransportExecutorOutcomes[value.ordinal()];
				transportExecutors.put(value, new Rs2PlannerShadowCoverageStatsFst(
						outcomes[Rs2PlannerShadowComparisonFst.Status.MATCH.ordinal()],
						outcomes[Rs2PlannerShadowComparisonFst.Status.DIVERGENCE.ordinal()],
						outcomes[Rs2PlannerShadowComparisonFst.Status.FAILED.ordinal()]));
			}
			EnumMap<Rs2TransportTypeFst, Rs2PlannerShadowCoverageStatsFst>
					transportTypes = new EnumMap<>(Rs2TransportTypeFst.class);
			for (Rs2TransportTypeFst value : Rs2TransportTypeFst.values())
			{
				long[] outcomes = shadowTransportTypeOutcomes[value.ordinal()];
				transportTypes.put(value, new Rs2PlannerShadowCoverageStatsFst(
						outcomes[Rs2PlannerShadowComparisonFst.Status.MATCH.ordinal()],
						outcomes[Rs2PlannerShadowComparisonFst.Status.DIVERGENCE.ordinal()],
						outcomes[Rs2PlannerShadowComparisonFst.Status.FAILED.ordinal()]));
			}
			return new Rs2PlannerShadowStatsFst(
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
					new Rs2WalkerShadowExecutionStatsFst(
							shadowWalkerArrivals,
							shadowWalkerUnreachable,
							shadowWalkerExits,
							shadowRecoveryArrivals,
							shadowRecoveryUnreachable,
							shadowRecoveryExits),
					new Rs2PlannerCanaryPerformanceStatsFst(
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

	private static PathTerminationReasonFst mapTermination(Rs2RouteTerminationFst reason)
	{
		if (reason == null)
		{
			return PathTerminationReasonFst.FAILED;
		}
		switch (reason)
		{
			case TARGET_REACHED:
				return PathTerminationReasonFst.TARGET_REACHED;
			case SEARCH_EXHAUSTED:
				return PathTerminationReasonFst.SEARCH_EXHAUSTED;
			case CUTOFF_REACHED:
				return PathTerminationReasonFst.CUTOFF_REACHED;
			case CANCELLED:
				return PathTerminationReasonFst.CANCELLED;
			case FAILED:
			default:
				return PathTerminationReasonFst.FAILED;
		}
	}

	static Rs2RoutePlannerFst localPlanner(PathfinderConfigFst config)
	{
		return new LocalRoutePlanner(config);
	}


	static boolean isF2pCanary(
			PlannerSelectionModeFst plannerMode, Rs2RouteRequestFst request)
	{
		return plannerMode != null
				&& plannerMode.f2pCanaryEnabled()
				&& request != null
				&& request.getPolicy().map(policy -> !policy.isMembersWorld()).orElse(false);
	}

	private static PlannerEvaluation evaluateUpstream(
			Rs2RouteRequestFst request,
			Rs2PlanningSnapshotFst snapshot,
			Rs2RouteResultFst local,
			Rs2PlannerShadowContextFst.Invocation invocation,
			boolean walkingOnlySelected)
	{
		Rs2PlannerShadowContextFst context = Rs2PlannerShadowContextFst.from(
				invocation, walkingOnlySelected, request, local);
		return evaluateUpstream(beginPlannerComparison(context), request, snapshot, local);
	}

	static void recordCanaryOutcome(
			Rs2PlannerShadowComparisonFst comparison,
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
	static boolean shouldSelectUpstream(Rs2PlannerShadowComparisonFst comparison)
	{
		return comparison != null
				&& comparison.getStatus() == Rs2PlannerShadowComparisonFst.Status.MATCH;
	}

	static Rs2PlanningSnapshotFst resolvePlanningSnapshot(
			Rs2RouteRequestFst request, PathfinderConfigFst config)
	{
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(config, "config");
		Rs2RoutePolicyFst policy = request.getPolicy().orElseThrow(
				() -> new IllegalArgumentException("planning snapshot requires a resolved policy"));
		Set<TransportFst> admitted = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		Map<WorldPoint, Set<TransportFst>> activeTransports = config.getTransports();
		if (activeTransports != null)
		{
			for (Set<TransportFst> values : activeTransports.values())
			{
				admitted.addAll(values);
			}
		}
		Set<TransportFst> usableTeleports = config.getUsableTeleportsSnapshot();
		if (usableTeleports != null)
		{
			admitted.addAll(usableTeleports);
		}
		List<Rs2TransportEdgeFst> edges = new ArrayList<>(admitted.size());
		for (TransportFst transport : admitted)
		{
			edges.add(toTransportEdge(transport));
		}
		net.runelite.client.plugins.microbot.walkerfst.shortestpath.pathfinder.live.LiveCollisionOverlayFst overlay =
				config.getLiveCollisionOverlay();
		LiveCollisionViewFst live = overlay == null ? null : overlay.current();
		Rs2PlanningSnapshotFst.CollisionOverride collision = live == null ? null : live::edge;
		Set<Long> blocked = config.getBlockedTransportEdgesPacked();
		return new Rs2PlanningSnapshotFst(
				policy,
				edges,
				collision,
				blocked == null ? Collections.emptySet() : new LinkedHashSet<>(blocked),
				config::isDangerousAdjacentTile);
	}

	private static void submitUpstreamShadow(
			Rs2RouteRequestFst request,
			Rs2PlanningSnapshotFst snapshot,
			Rs2RouteResultFst local,
			Rs2PlannerShadowContextFst.Invocation invocation,
			boolean walkingOnlySelected)
	{
		Rs2PlannerShadowContextFst context = Rs2PlannerShadowContextFst.from(
				invocation, walkingOnlySelected, request, local);
		PlannerComparisonTicket ticket = beginPlannerComparison(context);
		SHADOW_EXECUTOR.execute(() -> recordPlannerComparison(
				evaluateUpstream(ticket, request, snapshot, local)));
	}

	private static PlannerEvaluation evaluateUpstream(
			PlannerComparisonTicket ticket,
			Rs2RouteRequestFst request,
			Rs2PlanningSnapshotFst snapshot,
			Rs2RouteResultFst local)
	{
		Rs2RoutePlannerFst planner = upstreamPlanner();
		try
		{
			Rs2RouteResultFst candidate = planner.plan(request, snapshot);
			return new PlannerEvaluation(
					ticket,
					local,
					candidate,
					Rs2PlannerShadowComparisonFst.compare(
							planner.getEngineId(), ticket.context, local, candidate));
		}
		catch (RuntimeException failure)
		{
			return new PlannerEvaluation(
					ticket,
					local,
					null,
					Rs2PlannerShadowComparisonFst.failed(
							planner.getEngineId(), ticket.context, local, failure));
		}
	}

	static Rs2RoutePlannerFst upstreamPlanner()
	{
		UpstreamRoutePlannerFst delegate = new UpstreamRoutePlannerFst();
		if (Boolean.getBoolean("microbot.test.mode")
				&& Boolean.getBoolean("microbot.test.walker.forceUpstreamPlannerFailure"))
		{
			return new Rs2RoutePlannerFst()
			{
				@Override
				public String getEngineId()
				{
					return delegate.getEngineId();
				}

				@Override
				public Rs2RouteResultFst plan(
						Rs2RouteRequestFst request, Rs2PlanningSnapshotFst snapshot)
				{
					throw new IllegalStateException("synthetic upstream rollout failure");
				}
			};
		}
		return delegate;
	}

	private static void recordPlannerComparison(PlannerEvaluation evaluation)
	{
		Rs2PlannerShadowComparisonFst comparison = evaluation.comparison;
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
			for (Rs2PlannerShadowContextFst.Coverage value
					: comparison.getContext().getCoverage())
			{
				shadowCoverageOutcomes[value.ordinal()][comparison.getStatus().ordinal()]++;
			}
			for (Rs2TransportExecutorFst value
					: comparison.getContext().getTransportExecutors())
			{
				shadowTransportExecutorOutcomes[value.ordinal()]
						[comparison.getStatus().ordinal()]++;
			}
			for (Rs2TransportTypeFst value : comparison.getContext().getTransportTypes())
			{
				shadowTransportTypeOutcomes[value.ordinal()]
						[comparison.getStatus().ordinal()]++;
			}
			if (comparison.getStatus() != Rs2PlannerShadowComparisonFst.Status.FAILED
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
			Rs2PlannerShadowContextFst context)
	{
		synchronized (shadowEvidenceMutex)
		{
			long generation = ++shadowGeneration;
			shadowSubmitted++;
			lastShadowComparison = null;
			return new PlannerComparisonTicket(generation, context);
		}
	}

	private static Rs2TransportEdgeFst toTransportEdge(TransportFst transport)
	{
		List<Rs2TransportItemRequirementFst> itemRequirements = new ArrayList<>();
		for (TransportItemRequirementFst requirement : transport.getItemRequirements())
		{
			itemRequirements.add(new Rs2TransportItemRequirementFst(
					requirement.getAlternatives(),
					requirement.getStaffAlternatives(),
					requirement.getOffhandAlternatives(),
					requirement.isRuneOnly()));
		}
		return new Rs2TransportEdgeFst(
				transport.getOrigin(),
				transport.getDestination(),
				mapTransportType(transport.getType()),
				mapExecutor(TransportExecutionRegistryFst.executorFor(transport).orElse(null)),
				mapTerminalTravelMode(transport),
				transport.getDisplayInfo(),
				transport.getAction(),
				transport.getName(),
				transport.getObjectId(),
				transport.getDuration(),
				TransportTypeFst.isTeleport(transport.getType(), transport.getOrigin()),
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

	private static Rs2TransportExecutorFst mapExecutor(TransportExecutionRegistryFst.Executor executor)
	{
		if (executor == null)
		{
			return Rs2TransportExecutorFst.UNSUPPORTED;
		}
		try
		{
			return Rs2TransportExecutorFst.valueOf(executor.name());
		}
		catch (IllegalArgumentException ignored)
		{
			return Rs2TransportExecutorFst.UNSUPPORTED;
		}
	}

	private static Rs2TerminalTravelModeFst mapTerminalTravelMode(TransportFst transport)
	{
		return TransportExecutionRegistryFst.terminalTravelModeFor(transport)
				.map(mode -> Rs2TerminalTravelModeFst.valueOf(mode.name()))
				.orElse(Rs2TerminalTravelModeFst.UNSUPPORTED);
	}

	static Rs2RouteRequestFst resolvePolicy(
			Rs2RouteRequestFst request, PathfinderConfigFst config)
	{
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(config, "config");
		EnumSet<Rs2TransportTypeFst> enabledTypes = EnumSet.noneOf(Rs2TransportTypeFst.class);
		for (TransportTypeFst type : config.getEnabledTransportTypes())
		{
			enabledTypes.add(mapTransportType(type));
		}
		Set<WorldPoint> restrictedPoints = new LinkedHashSet<>();
		for (int packed : config.getRestrictedPointsPacked())
		{
			restrictedPoints.add(WorldPointUtilFst.unpackWorldPoint(packed));
		}
		Rs2RoutePolicyFst policy = new Rs2RoutePolicyFst(
				config.isUseBankItems(),
				config.isAvoidWilderness(),
				config.isAvoidDangerousNpcs(),
				config.isIgnoreTeleportAndItems(),
				Rs2WalkerFst.disableTeleports,
				config.isMembersWorld(),
				config.getLiveCollisionOverlay().isEnabled(),
				config.getCalculationCutoffMillis(),
				config.getDistanceBeforeUsingTeleport(),
				Rs2RoutePolicyFst.TeleportationItemMode.valueOf(
						config.getTeleportationItemPolicy().name()),
				enabledTypes,
				restrictedPoints);
		return request.withPolicy(policy);
	}

	private static Rs2TransportTypeFst mapTransportType(TransportTypeFst type)
	{
		if (type == null)
		{
			return Rs2TransportTypeFst.UNKNOWN;
		}
		try
		{
			return Rs2TransportTypeFst.valueOf(type.name());
		}
		catch (IllegalArgumentException ignored)
		{
			return Rs2TransportTypeFst.UNKNOWN;
		}
	}

	private static boolean shouldRefresh(Rs2RouteRequestFst request, PathfinderConfigFst config)
	{
		if (request.getUseBankItems() != null
				|| request.getRefreshPolicy() == Rs2RouteRequestFst.RefreshPolicy.ALWAYS)
		{
			return true;
		}
		return request.getRefreshPolicy() == Rs2RouteRequestFst.RefreshPolicy.IF_TRANSPORTS_EMPTY
				&& config.getTransports().isEmpty();
	}

}
