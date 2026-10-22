package net.runelite.client.plugins.microbot.walkerfst.enums;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@Getter
public enum TreesFst {
    NONE("None"),
	AUBURNVALE("Auburnvale", new WorldPoint(1365, 3319, 0)),
    FALADOR("Falador", new WorldPoint(3002, 3374, 0)),
    FARMING_GUILD("Farming Guild", new WorldPoint(1233, 3733, 0)),
    GNOME_STRONGHOLD("Gnome Stronghold", new WorldPoint(2437, 3418, 0)),
    LUMBRIDGE("Lumbridge", new WorldPoint(3195, 3230, 0)),
    TAVERLEY("Taverley", new WorldPoint(2936, 3441, 0)),
    VARROCK("Varrock", new WorldPoint(3227, 3457, 0));

    private final String name;
    private WorldPoint worldPoint;

    TreesFst(String name, WorldPoint worldPoint) {
        this.name = name;
        this.worldPoint = worldPoint;
    }

    TreesFst(String name) {
        this.name = name;
    }
}