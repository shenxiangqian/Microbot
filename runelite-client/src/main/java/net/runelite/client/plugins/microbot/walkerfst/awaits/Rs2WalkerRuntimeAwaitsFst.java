package net.runelite.client.plugins.microbot.walkerfst.awaits;

import net.runelite.client.plugins.microbot.walkerfst.shortestpath.pathfinder.PathfinderFst;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.function.BooleanSupplier;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public final class Rs2WalkerRuntimeAwaitsFst {
    private Rs2WalkerRuntimeAwaitsFst() {
    }

    public static boolean awaitPathfinderDone(PathfinderFst pathfinder, int timeoutMs) {
        if (pathfinder == null) {
            return false;
        }
        return sleepUntilTrue(pathfinder::isDone, 100, timeoutMs);
    }

    public static boolean awaitCondition(BooleanSupplier condition, int pollMs, int timeoutMs) {
        if (condition == null) {
            return false;
        }
        return sleepUntilTrue(condition, pollMs, timeoutMs);
    }

    public static boolean awaitMovementSettled(int pollMs, int timeoutMs) {
        return sleepUntilTrue(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(), pollMs, timeoutMs);
    }
}
