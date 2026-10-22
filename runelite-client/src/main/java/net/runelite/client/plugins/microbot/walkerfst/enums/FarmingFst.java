package net.runelite.client.plugins.microbot.walkerfst.enums;

import lombok.Getter;

@Getter
public enum FarmingFst {
    NONE("None"),
    ALLOTMENTS("Allotments"),
    BUSHES("Bushes"),
    COMPOST_BINS("Compost Bins"),
    FRUIT_TREES("Fruit Trees"),
    HERBS("Herbs"),
    HOPS("Hops"),
    TREES("Trees");

    private final String name;

    FarmingFst(String name) {
        this.name = name;
    }

}