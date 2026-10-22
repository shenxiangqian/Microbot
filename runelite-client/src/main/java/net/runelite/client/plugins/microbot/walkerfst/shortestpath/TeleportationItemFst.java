package net.runelite.client.plugins.microbot.walkerfst.shortestpath;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TeleportationItemFst {
    NONE("None"),
    INVENTORY("Inventory"),
    INVENTORY_NON_CONSUMABLE("Inventory (perm)"),
    INVENTORY_AND_BANK("Inventory + Bank");

    private final String type;

    public static boolean bankWalkingEnabled(ShortestPathConfigFst config) {
        return config != null && (config.walkWithBankedTransports()
                || config.useTeleportationItems() == INVENTORY_AND_BANK);
    }

    @Override
    public String toString() {
        return type;
    }

    public static TeleportationItemFst fromType(String type) {
        for (TeleportationItemFst teleportationItem : values()) {
            if (teleportationItem.type.equals(type)) {
                return teleportationItem;
            }
        }
        return null;
    }
}
