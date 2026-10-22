package net.runelite.client.plugins.microbot.walkerfst.lifecycle;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.WorldPointUtilFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.pathfinder.PathfinderFst;
import net.runelite.client.plugins.microbot.walkerfst.Rs2PathApiFst;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.walkerfst.Rs2WalkerFst;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Slf4j
public final class Rs2WalkerLifecycleRuntimeFst {

    private static final long PENDING_TRANSPORT_SUPPRESSION_TTL_MS = 15_000L;
    private static PendingTransportSuppression pendingTransportSuppression;

    private static final class PendingTransportSuppression {
        private final WorldPoint origin;
        private final WorldPoint destination;
        private final long createdAtMs;

        private PendingTransportSuppression(WorldPoint origin, WorldPoint destination, long createdAtMs) {
            this.origin = origin;
            this.destination = destination;
            this.createdAtMs = createdAtMs;
        }
    }

    private Rs2WalkerLifecycleRuntimeFst() {
    }

    public static void applyWalkerDestination(WorldPoint target) {
        if (target == null) {
            return;
        }
        if (!Microbot.isLoggedIn()) {
            log.warn("Unable to apply walker destination: not logged in");
            return;
        }
        Client client = Microbot.getClient();
        if (client == null) {
            log.warn("Unable to apply walker destination: client unavailable");
            return;
        }
        Player localPlayer = Microbot.getClientThread().invoke(() -> client.getLocalPlayer());
        if (!Rs2PathApiFst.isStartPointSet() && localPlayer == null) {
            log.warn("Start point is not set and player is null");
            return;
        }

        WorldMapPointManager wmm = Microbot.getWorldMapPointManager();
        if (wmm == null) {
            Rs2WalkerFst.clearWalkingRoute("walker:wmm-unavailable retry-setTarget dest=" + target);
            return;
        }
        wmm.removeIf(x -> x == Rs2PathApiFst.getMarker());
        WorldMapPoint marker = Rs2PathApiFst.createTargetMarker(target);
        Rs2PathApiFst.setMarker(marker);
        wmm.add(marker);

        WorldPoint start = Microbot.getClientThread().invoke(() -> {
            if (client.getTopLevelWorldView().isInstance()) {
                LocalPoint localLoc = Rs2Player.getLocalLocation();
                WorldPoint computed = localLoc != null ? WorldPoint.fromLocalInstance(client, localLoc) : null;
                if (computed == null) {
                    log.warn("[Walker] setTarget: instance localPoint conversion returned null (localLoc={} target={}) — falling back to raw world location",
                            localLoc, target);
                    computed = Rs2Player.getWorldLocation();
                }
                WorldPoint exitPortal = net.runelite.client.plugins.microbot.walkerfst.shortestpath.PohPanelFst.getExitPortalTile();
                if (exitPortal != null) {
                    Microbot.log("[Walker] In POH instance — remapping pathfinder start " + computed
                            + " -> exit portal " + exitPortal);
                    computed = exitPortal;
                }
                return computed;
            }
            return Rs2Player.getWorldLocation();
        });
        final PathfinderFst pathfinder = Rs2PathApiFst.getPathfinder();
        final WorldPoint effectiveStart = (Rs2PathApiFst.isStartPointSet() && pathfinder != null)
                ? pathfinder.getStart()
                : start;
        Rs2PathApiFst.setLastLocation(effectiveStart);
        Microbot.getClientThread().runOnSeperateThread(() -> restartPathfinding(effectiveStart, target));
    }

    public static boolean restartPathfinding(WorldPoint start, WorldPoint end) {
        return restartPathfinding(start, Set.of(end));
    }

    public static boolean restartPathfinding(WorldPoint start, Set<WorldPoint> ends) {
        PendingTransportSuppression suppression = consumePendingTransportSuppression(start, ends);
        return restartPathfinding(start, ends,
                suppression != null ? suppression.origin : null,
                suppression != null ? suppression.destination : null);
    }

    public static synchronized void suppressReverseTransportOnNextPath(WorldPoint origin,
                                                                        WorldPoint destination) {
        if (origin == null || destination == null) {
            pendingTransportSuppression = null;
            return;
        }
        // Cross-route carryover is needed for terminal surface/underground handoffs such as
        // Edgeville. Same coordinate-layer continuations are handled by the current route and are
        // not retained, avoiding an unrelated later walk inheriting a stale boat/door edge.
        if (sameCoordinateLayer(origin, destination)) {
            pendingTransportSuppression = null;
            return;
        }
        pendingTransportSuppression = new PendingTransportSuppression(
                origin, destination, System.currentTimeMillis());
    }

    private static synchronized PendingTransportSuppression consumePendingTransportSuppression(
            WorldPoint start, Set<WorldPoint> ends) {
        PendingTransportSuppression suppression = pendingTransportSuppression;
        if (suppression == null) {
            return null;
        }
        long ageMs = System.currentTimeMillis() - suppression.createdAtMs;
        if (ageMs < 0L || ageMs > PENDING_TRANSPORT_SUPPRESSION_TTL_MS
                || !sameLayerAndNear(start, suppression.origin, 3)) {
            pendingTransportSuppression = null;
            return null;
        }
        if (ends != null && ends.contains(start)) {
            return null;
        }
        boolean explicitReverseRequested = ends != null && ends.stream()
                .anyMatch(end -> sameCoordinateLayer(end, suppression.destination));
        pendingTransportSuppression = null;
        return explicitReverseRequested ? null : suppression;
    }

    private static boolean sameLayerAndNear(WorldPoint a, WorldPoint b, int distance) {
        return a != null
                && b != null
                && a.getPlane() == b.getPlane()
                && a.distanceTo2D(b) <= distance;
    }

    private static boolean sameCoordinateLayer(WorldPoint a, WorldPoint b) {
        return a != null
                && b != null
                && a.getPlane() == b.getPlane()
                && Math.floorDiv(a.getY(), WorldPointUtilFst.UNDERGROUND_Y_OFFSET)
                == Math.floorDiv(b.getY(), WorldPointUtilFst.UNDERGROUND_Y_OFFSET);
    }

    public static boolean restartPathfinding(WorldPoint start, Set<WorldPoint> ends,
                                             WorldPoint suppressedTransportOrigin,
                                             WorldPoint suppressedTransportDestination) {
        PathfinderFst pathfinder = Rs2PathApiFst.getPathfinder();
        if (pathfinder != null) {
            pathfinder.cancel();
            if (Rs2PathApiFst.getPathfinderFuture() != null) {
                Rs2PathApiFst.getPathfinderFuture().cancel(true);
            }
        }

        if (Rs2PathApiFst.getPathfindingExecutor() == null) {
            ThreadFactory shortestPathNaming = new ThreadFactoryBuilder().setNameFormat("shortest-path-%d").build();
            Rs2PathApiFst.setPathfindingExecutor(Executors.newSingleThreadExecutor(shortestPathNaming));
        }

        WorldPoint refreshTarget = ends != null && !ends.isEmpty() ? ends.iterator().next() : null;
        Rs2PathApiFst.getPathfinderConfig().refresh(refreshTarget);
        if (Rs2Player.isInCave()) {
            // Cave pathfinding runs synchronously, so no Future represents the pathfinder installed below.
            // Clear the cancelled asynchronous handle instead of leaving stale "work in flight" state.
            Rs2PathApiFst.setPathfinderFuture(null);
            pathfinder = new PathfinderFst(Rs2PathApiFst.getPathfinderConfig(), start, ends,
                    suppressedTransportOrigin, suppressedTransportDestination);
            pathfinder.run();
            try {
                Rs2PathApiFst.getPathfinderConfig().setIgnoreTeleportAndItems(true);
                PathfinderFst pathfinderWithoutTeleports = new PathfinderFst(Rs2PathApiFst.getPathfinderConfig(), start, ends,
                        suppressedTransportOrigin, suppressedTransportDestination);
                pathfinderWithoutTeleports.run();

                boolean noTeleportPathAvailable = !pathfinderWithoutTeleports.getPath().isEmpty();
                boolean basePathAvailable = pathfinder != null && !pathfinder.getPath().isEmpty();
                if (!noTeleportPathAvailable) {
                    Rs2PathApiFst.setPathfinder(basePathAvailable ? pathfinder : pathfinderWithoutTeleports);
                    return true;
                }

                WorldPoint lastPath = pathfinderWithoutTeleports.getPath().get(pathfinderWithoutTeleports.getPath().size() - 1);
                int reachedDistance = Rs2WalkerFst.config != null ? Rs2WalkerFst.config.reachedDistance() : 10;
                boolean pathWithoutTeleportsIsReachable = lastPath.distanceTo(ends.stream().findFirst().orElse(lastPath)) <= reachedDistance;
                if (pathWithoutTeleportsIsReachable
                        && basePathAvailable
                        && pathfinder.getPath().size() >= pathfinderWithoutTeleports.getPath().size()) {
                    Rs2PathApiFst.setPathfinder(pathfinderWithoutTeleports);
                } else {
                    Rs2PathApiFst.setPathfinder(basePathAvailable ? pathfinder : pathfinderWithoutTeleports);
                }
            } finally {
                Rs2PathApiFst.getPathfinderConfig().setIgnoreTeleportAndItems(false);
            }
        } else {
            Rs2PathApiFst.setPathfinder(new PathfinderFst(Rs2PathApiFst.getPathfinderConfig(), start, ends,
                    suppressedTransportOrigin, suppressedTransportDestination));
            Rs2PathApiFst.setPathfinderFuture(Rs2PathApiFst.getPathfindingExecutor().submit(Rs2PathApiFst.getPathfinder()));
        }
        return true;
    }
}
