package net.runelite.client.plugins.microbot.SDK.Util;

import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

public class Rs2TileItemUtil {


    /**
     * 检查 item 是否在屏幕上
     *
     * @param item 要检查的 item
     * @return true 如果在屏幕上，否则 false
     */
    public static boolean isOnScreen(Rs2TileItemModel item) {
        if (item == null) return false;

        Point screenPoint = getScreenPoint(item);
        if (screenPoint == null) return false;

        Client client = Microbot.getClient();
        if (client == null || client.getCanvas() == null) return false;

        Dimension dimension = client.getCanvas().getSize();

        return screenPoint.getX() >= 0 &&
                screenPoint.getX() <= dimension.getWidth() &&
                screenPoint.getY() >= 0 &&
                screenPoint.getY() <= dimension.getHeight();
    }

    /**
     * 获取 item 的屏幕坐标点
     *
     * @param item 要获取坐标的 item
     * @return 屏幕坐标点，如果不在屏幕上返回 null
     */
    public static Point getScreenPoint(Rs2TileItemModel item) {
        if (item == null) return null;

        Client client = Microbot.getClient();
        if (client == null) return null;

        LocalPoint localPoint = item.getLocalLocation();

        WorldPoint worldPoint = item.getWorldLocation();

        // 使用 localToCanvas 而不是 localToScreen
        return Perspective.localToCanvas(
                client,
                localPoint,
                worldPoint.getPlane()
        );
    }
}
