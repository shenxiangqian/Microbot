package net.runelite.client.plugins.microbot.walkerfst;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Immutable process-lifetime aggregate for production planner shadow evidence. */
public final class Rs2PlannerShadowStatsFst
{
	private final long submitted;
	private final long completed;
	private final long matches;
	private final long divergences;
	private final long failures;
	private final long staleResults;
	private final long discarded;
	private final long routeShapeDifferences;
	private final long upstreamCanarySelections;
	private final long localFallbackDivergences;
	private final long localFallbackFailures;
	private final long startedAtEpochMillis;
	private final Map<Rs2PlannerShadowContextFst.Coverage, Rs2PlannerShadowCoverageStatsFst> coverage;
	private final Map<Rs2TransportExecutorFst, Rs2PlannerShadowCoverageStatsFst> transportExecutors;
	private final Map<Rs2TransportTypeFst, Rs2PlannerShadowCoverageStatsFst> transportTypes;
	private final Rs2WalkerShadowExecutionStatsFst execution;
	private final Rs2PlannerCanaryPerformanceStatsFst canaryPerformance;

	Rs2PlannerShadowStatsFst(
		long submitted,
		long completed,
		long matches,
		long divergences,
		long failures,
		long staleResults,
		long discarded,
		long routeShapeDifferences,
		long upstreamCanarySelections,
		long localFallbackDivergences,
		long localFallbackFailures,
		long startedAtEpochMillis,
		Map<Rs2PlannerShadowContextFst.Coverage, Rs2PlannerShadowCoverageStatsFst> coverage,
		Map<Rs2TransportExecutorFst, Rs2PlannerShadowCoverageStatsFst> transportExecutors,
		Map<Rs2TransportTypeFst, Rs2PlannerShadowCoverageStatsFst> transportTypes,
		Rs2WalkerShadowExecutionStatsFst execution,
		Rs2PlannerCanaryPerformanceStatsFst canaryPerformance)
	{
		this.submitted = requireNonNegative(submitted, "submitted");
		this.completed = requireNonNegative(completed, "completed");
		this.matches = requireNonNegative(matches, "matches");
		this.divergences = requireNonNegative(divergences, "divergences");
		this.failures = requireNonNegative(failures, "failures");
		this.staleResults = requireNonNegative(staleResults, "staleResults");
		this.discarded = requireNonNegative(discarded, "discarded");
		this.routeShapeDifferences = requireNonNegative(
			routeShapeDifferences, "routeShapeDifferences");
		this.upstreamCanarySelections = requireNonNegative(
			upstreamCanarySelections, "upstreamCanarySelections");
		this.localFallbackDivergences = requireNonNegative(
			localFallbackDivergences, "localFallbackDivergences");
		this.localFallbackFailures = requireNonNegative(
			localFallbackFailures, "localFallbackFailures");
		this.startedAtEpochMillis = requireNonNegative(startedAtEpochMillis, "startedAtEpochMillis");
		EnumMap<Rs2PlannerShadowContextFst.Coverage, Rs2PlannerShadowCoverageStatsFst> copy =
			new EnumMap<>(Rs2PlannerShadowContextFst.Coverage.class);
		copy.putAll(coverage);
		this.coverage = Collections.unmodifiableMap(copy);
		EnumMap<Rs2TransportExecutorFst, Rs2PlannerShadowCoverageStatsFst> executorCopy =
			new EnumMap<>(Rs2TransportExecutorFst.class);
		executorCopy.putAll(transportExecutors);
		this.transportExecutors = Collections.unmodifiableMap(executorCopy);
		EnumMap<Rs2TransportTypeFst, Rs2PlannerShadowCoverageStatsFst> typeCopy =
			new EnumMap<>(Rs2TransportTypeFst.class);
		typeCopy.putAll(transportTypes);
		this.transportTypes = Collections.unmodifiableMap(typeCopy);
		this.execution = java.util.Objects.requireNonNull(execution, "execution");
		this.canaryPerformance = java.util.Objects.requireNonNull(
			canaryPerformance, "canaryPerformance");
		if (matches + divergences + failures != completed)
		{
			throw new IllegalArgumentException(
				"completed comparisons must equal matches, divergences and failures");
		}
	}

	private static long requireNonNegative(long value, String name)
	{
		if (value < 0)
		{
			throw new IllegalArgumentException(name + " must be non-negative");
		}
		return value;
	}

	public long getSubmitted() { return submitted; }
	public long getCompleted() { return completed; }
	public long getMatches() { return matches; }
	public long getDivergences() { return divergences; }
	public long getFailures() { return failures; }
	public long getStaleResults() { return staleResults; }
	public long getDiscarded() { return discarded; }
	public long getRouteShapeDifferences() { return routeShapeDifferences; }
	public long getUpstreamCanarySelections() { return upstreamCanarySelections; }
	public long getLocalFallbackDivergences() { return localFallbackDivergences; }
	public long getLocalFallbackFailures() { return localFallbackFailures; }
	public long getStartedAtEpochMillis() { return startedAtEpochMillis; }
	public Map<Rs2PlannerShadowContextFst.Coverage, Rs2PlannerShadowCoverageStatsFst> getCoverage()
	{
		return coverage;
	}
	public Map<Rs2TransportExecutorFst, Rs2PlannerShadowCoverageStatsFst> getTransportExecutors()
	{
		return transportExecutors;
	}
	public Map<Rs2TransportTypeFst, Rs2PlannerShadowCoverageStatsFst> getTransportTypes()
	{
		return transportTypes;
	}
	public Rs2WalkerShadowExecutionStatsFst getExecution() { return execution; }
	public Rs2PlannerCanaryPerformanceStatsFst getCanaryPerformance() { return canaryPerformance; }

	public long getPending()
	{
		return Math.max(0L, submitted - completed - discarded);
	}
}
