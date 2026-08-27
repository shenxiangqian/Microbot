package net.runelite.client.plugins.microbot.SDK.Util;

import net.runelite.api.*;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

import java.awt.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

public class Rs2TileObjectUtil {

    /**
     * 检查object是否包含某些选项
     * @param object 要检查的对象
     * @param action 要包含的选项
     * @return true 如果包含，否则false
     */
    public static boolean hasAction(Rs2TileObjectModel object,String... action) {
        ObjectComposition comp = object.getObjectComposition();
        EntityOps ops = comp.getOps();
        return IntStream.range(0,ops.getNumOps())
                .mapToObj(ops::getOp)
                .filter(Objects::nonNull)
                .anyMatch(op-> Arrays.stream(action).anyMatch(e->e.equalsIgnoreCase(op)));

    }

    /**
     * 检查 object 是否在屏幕上
     *
     * @param object 要检查的 object
     * @return true 如果在屏幕上，否则 false
     */
    public static boolean isOnScreen(Rs2TileObjectModel object) {
        if (object == null) return false;

        Point screenPoint = getScreenPoint(object);
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
     * 获取 object 的屏幕坐标点
     *
     * @param object 要获取坐标的 object
     * @return 屏幕坐标点，如果不在屏幕上返回 null
     */
    public static Point getScreenPoint(Rs2TileObjectModel object) {
        if (object == null) return null;

        Client client = Microbot.getClient();
        if (client == null) return null;

        LocalPoint localPoint = object.getLocalLocation();

        WorldPoint worldPoint = object.getWorldLocation();

        // 使用 localToCanvas 而不是 localToScreen
        return Perspective.localToCanvas(
                client,
                localPoint,
                worldPoint.getPlane()
        );
    }
}
