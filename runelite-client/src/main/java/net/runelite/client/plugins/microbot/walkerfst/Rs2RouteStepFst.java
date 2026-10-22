package net.runelite.client.plugins.microbot.walkerfst;

import net.runelite.api.coords.WorldPoint;

import java.util.Objects;
import java.util.Optional;

/** One immutable, planner-independent edge in a route. */
public final class Rs2RouteStepFst
{
	public enum Kind
	{
		WALK,
		TRANSPORT
	}

	private final WorldPoint from;
	private final WorldPoint to;
	private final Kind kind;
	private final Rs2TransportEdgeFst transport;

	private Rs2RouteStepFst(WorldPoint from, WorldPoint to, Kind kind, Rs2TransportEdgeFst transport)
	{
		this.from = Objects.requireNonNull(from, "from");
		this.to = Objects.requireNonNull(to, "to");
		this.kind = Objects.requireNonNull(kind, "kind");
		this.transport = transport;
		if ((kind == Kind.TRANSPORT) != (transport != null))
		{
			throw new IllegalArgumentException("transport metadata must match route-step kind");
		}
		if (transport != null && !to.equals(transport.getDestination()))
		{
			throw new IllegalArgumentException("transport destination must match route-step destination");
		}
	}

	public static Rs2RouteStepFst walk(WorldPoint from, WorldPoint to)
	{
		return new Rs2RouteStepFst(from, to, Kind.WALK, null);
	}

	public static Rs2RouteStepFst transport(WorldPoint from, WorldPoint to, Rs2TransportEdgeFst transport)
	{
		return new Rs2RouteStepFst(from, to, Kind.TRANSPORT, transport);
	}

	public WorldPoint getFrom() { return from; }
	public WorldPoint getTo() { return to; }
	public Kind getKind() { return kind; }
	public Optional<Rs2TransportEdgeFst> getTransport() { return Optional.ofNullable(transport); }
	public boolean isTransport() { return kind == Kind.TRANSPORT; }
}
