package net.runelite.client.plugins.microbot.walkerfst;

/** Immutable summary of the latest completed production shadow comparison. */
public final class Rs2PlannerShadowComparisonFst
{
	public enum Status
	{
		MATCH,
		DIVERGENCE,
		FAILED
	}

	private final Status status;
	private final String shadowEngineId;
	private final Rs2PlannerShadowContextFst context;
	private final boolean terminationMatches;
	private final boolean endpointMatches;
	private final boolean costComparable;
	private final boolean costMatches;
	private final boolean selectedTransportsMatch;
	private final boolean pathMatches;
	private final long shadowSearchNanos;
	private final long localSearchNanos;
	private final String failureType;

	private Rs2PlannerShadowComparisonFst(
		Status status,
		String shadowEngineId,
		Rs2PlannerShadowContextFst context,
		boolean terminationMatches,
		boolean endpointMatches,
		boolean costComparable,
		boolean costMatches,
		boolean selectedTransportsMatch,
		boolean pathMatches,
		long shadowSearchNanos,
		long localSearchNanos,
		String failureType)
	{
		this.status = status;
		this.shadowEngineId = shadowEngineId;
		this.context = context;
		this.terminationMatches = terminationMatches;
		this.endpointMatches = endpointMatches;
		this.costComparable = costComparable;
		this.costMatches = costMatches;
		this.selectedTransportsMatch = selectedTransportsMatch;
		this.pathMatches = pathMatches;
		this.shadowSearchNanos = shadowSearchNanos;
		this.localSearchNanos = localSearchNanos;
		this.failureType = failureType;
	}

	static Rs2PlannerShadowComparisonFst compare(
		String engineId,
		Rs2PlannerShadowContextFst context,
		Rs2RouteResultFst local,
		Rs2RouteResultFst shadow)
	{
		boolean termination = local.getTerminationReason() == shadow.getTerminationReason();
		boolean endpoint = local.getEndpoint().equals(shadow.getEndpoint());
		boolean comparable = local.getMetrics().hasPathCost() && shadow.getMetrics().hasPathCost();
		boolean cost = comparable
			&& local.getMetrics().getPathCost() == shadow.getMetrics().getPathCost();
		boolean transports = sameSelectedTransports(local, shadow);
		boolean path = local.getPath().equals(shadow.getPath());
		Status status = termination && endpoint && comparable && cost && transports
			? Status.MATCH : Status.DIVERGENCE;
		return new Rs2PlannerShadowComparisonFst(
			status, engineId, context, termination, endpoint, comparable, cost, transports, path,
			shadow.getMetrics().getSearchNanos(), local.getMetrics().getSearchNanos(), null);
	}

	static Rs2PlannerShadowComparisonFst failed(
		String engineId, Rs2PlannerShadowContextFst context, Rs2RouteResultFst local,
		RuntimeException failure)
	{
		return new Rs2PlannerShadowComparisonFst(
			Status.FAILED, engineId, context, false, false, false, false, false, false,
			Rs2RouteMetricsFst.UNAVAILABLE, local.getMetrics().getSearchNanos(),
			failure.getClass().getSimpleName());
	}

	private static boolean sameSelectedTransports(Rs2RouteResultFst local, Rs2RouteResultFst shadow)
	{
		java.util.List<Rs2RouteStepFst> localSteps = local.getTransportSteps();
		java.util.List<Rs2RouteStepFst> shadowSteps = shadow.getTransportSteps();
		if (localSteps.size() != shadowSteps.size())
		{
			return false;
		}
		for (int i = 0; i < localSteps.size(); i++)
		{
			Rs2TransportEdgeFst localEdge = localSteps.get(i).getTransport().orElseThrow(
				IllegalStateException::new);
			Rs2TransportEdgeFst shadowEdge = shadowSteps.get(i).getTransport().orElseThrow(
				IllegalStateException::new);
			if (localEdge.getSourceIdentity() != shadowEdge.getSourceIdentity())
			{
				return false;
			}
		}
		return true;
	}

	public Status getStatus() { return status; }
	public String getShadowEngineId() { return shadowEngineId; }
	public Rs2PlannerShadowContextFst getContext() { return context; }
	public boolean isTerminationMatches() { return terminationMatches; }
	public boolean isEndpointMatches() { return endpointMatches; }
	public boolean isCostComparable() { return costComparable; }
	public boolean isCostMatches() { return costMatches; }
	public boolean isSelectedTransportsMatch() { return selectedTransportsMatch; }
	public boolean isPathMatches() { return pathMatches; }
	public long getShadowSearchNanos() { return shadowSearchNanos; }
	public long getLocalSearchNanos() { return localSearchNanos; }
	public String getFailureType() { return failureType; }
}
