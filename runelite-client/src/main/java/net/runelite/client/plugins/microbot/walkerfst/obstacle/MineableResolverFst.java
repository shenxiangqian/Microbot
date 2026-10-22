package net.runelite.client.plugins.microbot.walkerfst.obstacle;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

import java.util.Arrays;
import java.util.List;

/**
 * Resolves a mineable obstacle (a Motherlode Mine rockfall) sitting on a planned edge (P2;
 * docs/walker-p2-unification.md).
 * <p>
 * Adapter stage: {@link #handles} is a pure classification over the injected {@link LiveScene} (is there a
 * rockfall object on the edge?), so it is unit-testable headlessly; {@link #resolve} delegates the action to
 * the already-decomposed {@code Rs2ObstacleHandler}. A later P2 step rewrites the action against
 * {@link WalkerActions} and deletes the delegate.
 */
public final class MineableResolverFst implements ObstacleResolverFst {

    @Override
    public boolean handles(PlannedEdgeFst edge, LiveSceneFst scene) {
        if (edge == null || scene == null) {
            return false;
        }
        return isRockfall(scene.objectAt(edge.to())) || isRockfall(scene.objectAt(edge.from()));
    }

    @Override
    public ObstacleResolutionFst resolve(PlannedEdgeFst edge, LiveSceneFst scene, WalkerActionsFst actions) {
        final List<WorldPoint> miniPath = Arrays.asList(edge.from(), edge.to());
        switch (net.runelite.client.plugins.microbot.walkerfst.obstacle.Rs2ObstacleHandlerFst
                .handleRockfall(miniPath, 0)) {
            case MINED:
                return ObstacleResolutionFst.interacted();
            case NO_PICKAXE:
                return ObstacleResolutionFst.abort("motherlode-rockfall-no-pickaxe");
            case NOT_APPLICABLE:
            default:
                return ObstacleResolutionFst.notApplicable();
        }
    }

    private static boolean isRockfall(TileObject object) {
        return object != null
                && (object.getId() == ObjectID.MOTHERLODE_ROCKFALL_1
                || object.getId() == ObjectID.MOTHERLODE_ROCKFALL_2);
    }
}
