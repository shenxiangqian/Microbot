package net.runelite.client.plugins.microbot.shortestpath;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TeleportationItem {
    NONE("None"),
    INVENTORY("Inventory"),
    INVENTORY_NON_CONSUMABLE("Inventory (perm)"),
    INVENTORY_AND_BANK("Inventory + Bank");

    private final String type;

    public static boolean bankWalkingEnabled(ShortestPathConfig config) {
        return config != null && (config.walkWithBankedTransports()
                || config.useTeleportationItems() == INVENTORY_AND_BANK);
    }

    @Override
    public String toString() {
        return type;
    }

    public static TeleportationItem fromType(String type) {
        for (TeleportationItem teleportationItem : values()) {
            if (teleportationItem.type.equals(type)) {
                return teleportationItem;
            }
        }
        return null;
    }
}
