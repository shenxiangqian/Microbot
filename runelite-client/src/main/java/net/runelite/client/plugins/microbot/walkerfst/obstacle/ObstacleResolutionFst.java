package net.runelite.client.plugins.microbot.walkerfst.obstacle;

import net.runelite.api.coords.WorldPoint;

import java.util.Objects;

/**
 * Outcome of an {@link ObstacleResolver} inspecting/handling a {@link PlannedEdge}. One result type for
 * every obstacle kind (door / rockfall / shortcut / transport), so the walker's recovery has a single
 * {@code switch} instead of forty special-cased branches (P2; docs/walker-p2-unification.md).
 */
public final class ObstacleResolutionFst {

    public enum Kind {
        /** This resolver does not apply to the edge — try the next resolver / fall back. */
        NOT_APPLICABLE,
        /** The edge is now traversable; the walker may continue the route. */
        CROSSED,
        /** An interaction was issued (door opening, rock mining) — yield a tick and re-check. */
        INTERACTED,
        /** Still settling from a prior interaction — yield without re-issuing. */
        WAITING,
        /** The player must first step onto {@link #walkTarget} (e.g. a stepping-stone origin) to proceed. */
        WALK_TO_ORIGIN,
        /** Cannot resolve; the caller should record the real blocked edge and recalculate. */
        ABORT
    }

    private static final ObstacleResolutionFst NOT_APPLICABLE = new ObstacleResolutionFst(Kind.NOT_APPLICABLE, null, null);
    private static final ObstacleResolutionFst CROSSED = new ObstacleResolutionFst(Kind.CROSSED, null, null);
    private static final ObstacleResolutionFst INTERACTED = new ObstacleResolutionFst(Kind.INTERACTED, null, null);
    private static final ObstacleResolutionFst WAITING = new ObstacleResolutionFst(Kind.WAITING, null, null);

    private final Kind kind;
    private final WorldPoint walkTarget;
    private final String reason;

    private ObstacleResolutionFst(Kind kind, WorldPoint walkTarget, String reason) {
        this.kind = kind;
        this.walkTarget = walkTarget;
        this.reason = reason;
    }

    public static ObstacleResolutionFst notApplicable() {
        return NOT_APPLICABLE;
    }

    public static ObstacleResolutionFst crossed() {
        return CROSSED;
    }

    public static ObstacleResolutionFst interacted() {
        return INTERACTED;
    }

    public static ObstacleResolutionFst waiting() {
        return WAITING;
    }

    public static ObstacleResolutionFst walkToOrigin(WorldPoint target) {
        return new ObstacleResolutionFst(Kind.WALK_TO_ORIGIN,
                Objects.requireNonNull(target, "target"), null);
    }

    public static ObstacleResolutionFst abort(String reason) {
        return new ObstacleResolutionFst(Kind.ABORT, null, reason);
    }

    public Kind kind() {
        return kind;
    }

    /** The tile to step onto, when {@link #kind()} is {@link Kind#WALK_TO_ORIGIN}; otherwise {@code null}. */
    public WorldPoint walkTarget() {
        return walkTarget;
    }

    /** The abort reason, when {@link #kind()} is {@link Kind#ABORT}; otherwise {@code null}. */
    public String reason() {
        return reason;
    }

    @Override
    public String toString() {
        return "ObstacleResolution{" + kind
                + (walkTarget != null ? " walkTarget=" + walkTarget : "")
                + (reason != null ? " reason=" + reason : "") + "}";
    }
}
