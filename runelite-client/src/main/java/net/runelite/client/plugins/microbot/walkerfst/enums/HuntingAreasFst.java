package net.runelite.client.plugins.microbot.walkerfst.enums;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

@Getter
public enum HuntingAreasFst {
    NONE("None"),
    BIRDS("Birds"),
    CHINCHOMPAS("Chinchompas"),
    INSECTS("Insects"),
    KEBBITS("Kebbits"),
    SALAMANDERS("Salamanders"),
    SPECIAL("Special");

    private final String name;

    HuntingAreasFst(String name) {
        this.name = name;
    }
}
