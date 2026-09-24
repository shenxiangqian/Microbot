package net.runelite.client.plugins.microbot.util.walker.banking;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;

import java.util.List;

/** A bank detour survives MOVING callbacks; a completed or failed detour continues directly. */
@Getter
public final class BankedWalkPlan {
    private final WorldPoint target;
    private final WorldPoint bank;
    private final List<Transport> transports;

    public BankedWalkPlan(WorldPoint target, WorldPoint bank, List<Transport> transports) {
        this.target = target;
        this.bank = bank;
        this.transports = List.copyOf(transports);
    }

    public BankedWalkPlan continueDirectly() {
        return new BankedWalkPlan(target, null, List.of());
    }
}
