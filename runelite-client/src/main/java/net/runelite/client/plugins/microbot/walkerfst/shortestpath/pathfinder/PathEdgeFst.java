package net.runelite.client.plugins.microbot.walkerfst.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.WorldPointUtilFst;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One materialized edge in a chosen local pathfinder route. */
public final class PathEdgeFst
{
	private final WorldPoint from;
	private final WorldPoint to;
	private final TransportFst transport;

	PathEdgeFst(WorldPoint from, WorldPoint to, TransportFst transport)
	{
		this.from = from;
		this.to = to;
		this.transport = transport;
	}

	static List<PathEdgeFst> fromForwardChain(NodeFst lastNode)
	{
		if (lastNode == null)
		{
			return Collections.emptyList();
		}
		List<NodeFst> nodes = lastNode.getNodePath();
		List<PathEdgeFst> edges = new ArrayList<>(Math.max(0, nodes.size() - 1));
		for (int i = 1; i < nodes.size(); i++)
		{
			NodeFst from = nodes.get(i - 1);
			NodeFst to = nodes.get(i);
			TransportFst transport = to instanceof TransportNodeFst
				? ((TransportNodeFst) to).getTransport()
				: null;
			edges.add(new PathEdgeFst(
				WorldPointUtilFst.unpackWorldPoint(from.packedPosition),
				WorldPointUtilFst.unpackWorldPoint(to.packedPosition),
				transport));
		}
		return Collections.unmodifiableList(edges);
	}

	/**
	 * Build the temporary local compatibility view for a completed engine-neutral route.
	 * The transport list is aligned with path edges and may contain {@code null} walking entries.
	 */
	static List<PathEdgeFst> fromMaterializedRoute(
		List<WorldPoint> path, List<TransportFst> transportsByStep)
	{
		if (path == null || transportsByStep == null)
		{
			throw new IllegalArgumentException("materialized path and transports are required");
		}
		int expected = Math.max(0, path.size() - 1);
		if (transportsByStep.size() != expected)
		{
			throw new IllegalArgumentException(
				"materialized transport count must match path edge count");
		}
		List<PathEdgeFst> edges = new ArrayList<>(expected);
		for (int i = 0; i < expected; i++)
		{
			WorldPoint from = path.get(i);
			WorldPoint to = path.get(i + 1);
			if (from == null || to == null)
			{
				throw new IllegalArgumentException("materialized path points must be non-null");
			}
			TransportFst transport = transportsByStep.get(i);
			if (transport != null && !to.equals(transport.getDestination()))
			{
				throw new IllegalArgumentException(
					"materialized transport destination must match its route step");
			}
			edges.add(new PathEdgeFst(from, to, transport));
		}
		return Collections.unmodifiableList(edges);
	}

	/**
	 * Combine a normal start-to-meeting chain with the reverse-search meeting-to-goal chain.
	 * Reverse transport metadata belongs to the {@code from} node, unlike a forward chain where it
	 * belongs to the {@code to} node.
	 */
	static List<PathEdgeFst> fromBidirectionalChains(NodeFst forwardAtMeet, NodeFst backwardAtMeet)
	{
		List<PathEdgeFst> edges = new ArrayList<>(fromForwardChain(forwardAtMeet));
		for (NodeFst from = backwardAtMeet; from != null && from.previous != null; from = from.previous)
		{
			NodeFst to = from.previous;
			TransportFst transport = from instanceof TransportNodeFst
				? ((TransportNodeFst) from).getTransport()
				: null;
			edges.add(new PathEdgeFst(
				WorldPointUtilFst.unpackWorldPoint(from.packedPosition),
				WorldPointUtilFst.unpackWorldPoint(to.packedPosition),
				transport));
		}
		return Collections.unmodifiableList(edges);
	}

	public WorldPoint getFrom()
	{
		return from;
	}

	public WorldPoint getTo()
	{
		return to;
	}

	public TransportFst getTransport()
	{
		return transport;
	}

	public boolean isTransport()
	{
		return transport != null;
	}
}
