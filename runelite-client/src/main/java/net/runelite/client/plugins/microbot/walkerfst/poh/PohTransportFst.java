package net.runelite.client.plugins.microbot.walkerfst.poh;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportFst;
import net.runelite.client.plugins.microbot.walkerfst.shortestpath.TransportTypeFst;

/**
 * PohTransport 的 Fst 副本：继承 TransportFst，使用 POH 传送系统的传送方式。
 */
public class PohTransportFst extends TransportFst {

    @Getter
    private final PohTeleport teleport;

    public PohTransportFst(WorldPoint exitPortalPoint, PohTeleport teleport) {
        super(
                java.util.Objects.requireNonNull(exitPortalPoint, "exitPortalPoint is null"),
                java.util.Objects.requireNonNull(teleport, "teleport is null").getDestination(),
                teleport.displayInfo(), TransportTypeFst.POH, true, teleport.getDuration()
        );
        this.teleport = teleport;
    }

    /**
     * 执行 POH 传送动作。
     *
     * @return 传送成功返回 true
     */
    public boolean execute() {
        return teleport.execute();
    }

}
