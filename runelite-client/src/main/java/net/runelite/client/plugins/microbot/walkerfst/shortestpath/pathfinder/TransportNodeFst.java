package net.runelite.client.plugins.microbot.walkerfst.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportFst;

public class TransportNodeFst extends NodeFst implements Comparable<TransportNodeFst> {
    private final TransportFst transport;

    public TransportNodeFst(WorldPoint point, NodeFst previous, int travelTime, TransportFst transport) {
        // Use Node(int, Node, int cost) which assigns cost directly. The WorldPoint
        // Node constructor re-adds previous.cost via its cost(previous, wait) method,
        // which caused (a) double-counting when we passed prev.cost + travelTime as
        // wait and (b) integer overflow for plane-crossing transports with travelTime=0
        // because its distance fallback returns Integer.MAX_VALUE across planes.
        super(net.runelite.client.plugins.microbot.walkerfst.shortestpath.WorldPointUtilFst.packWorldPoint(point),
                previous,
                (previous != null ? previous.cost : 0) + travelTime);
        this.transport = transport;
    }

    public TransportFst getTransport() {
        return transport;
    }

    @Override
    public int compareTo(TransportNodeFst other) {
        return Integer.compare(cost, other.cost);
    }
}
