package net.runelite.client.plugins.microbot.util.walker;

import java.awt.geom.AffineTransform;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.SpriteID;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.Varbits;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.annotation.Nullable;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class Rs2MiniMap {

    /**
     * Converts a {@link LocalPoint} to a minimap coordinate {@link Point}.
     *
     * @param localPoint The local point to convert.
     * @return The corresponding minimap point, or {@code null} if conversion fails.
     */
    @Nullable
    public static Point localToMinimap(LocalPoint localPoint) {
        if (localPoint == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Perspective.localToMinimap(Microbot.getClient(), localPoint))
                .orElse(null);
    }

    /**
     * Converts a {@link WorldPoint} to a minimap coordinate {@link Point}.
     *
     * @param worldPoint The world point to convert.
     * @return The corresponding minimap point, or {@code null} if conversion fails.
     */
    @Nullable
    public static Point worldToMinimap(WorldPoint worldPoint) {
        if (worldPoint == null) return null;

        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);

        if (Microbot.getClient().getTopLevelWorldView().isInstance() && localPoint == null) {
            localPoint = Rs2LocalPoint.fromWorldInstance(worldPoint);
        }

        if (localPoint == null) {
            return null;
        }

        final LocalPoint lp = localPoint;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Perspective.localToMinimap(Microbot.getClient(), lp))
                .orElse(null);
    }

    /**
     * Retrieves the minimap draw widget based on the current game view mode.
     *
     * @return The minimap draw widget, or {@code null} if not found.
     */
    public static Widget getMinimapDrawWidget() {
        if (Microbot.getClient().isResized()) {
            if (Microbot.getVarbitValue(Varbits.SIDE_PANELS) == 1) {
                return Rs2Widget.getWidget(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA);
            }
            return Rs2Widget.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA);
        }
        return Rs2Widget.getWidget(ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);
    }

    /**
     * Returns a simple elliptical clip area for the minimap.
     *
     * @return A {@link Shape} representing the minimap clip area.
     */
    private static Shape getMinimapClipAreaSimple() {
        Widget minimapDrawArea = getMinimapDrawWidget();
        if (minimapDrawArea == null) {
            return null;
        }
        Rectangle bounds = minimapDrawArea.getBounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return null;
        }
        return new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
    }

	/**
	 * 小地图可点击区域的缓存项。
	 *
	 * <p>多边形完全由「缩放方式 + 缩放系数 + 控件矩形」决定：alpha 蒙版是静态 UI 贴图，
	 * 控件矩形只在改窗口大小时变。所以这三个量不变就可以直接复用上一次算好的多边形，
	 * 不必再逐像素扫描蒙版。</p>
	 */
	private static final class ClipAreaCache
	{
		private final boolean resized;
		private final double scale;
		private final int x;
		private final int y;
		private final int width;
		private final int height;
		private final Shape shape;

		private ClipAreaCache(boolean resized, double scale, Rectangle bounds, Shape shape)
		{
			this.resized = resized;
			this.scale = scale;
			this.x = bounds.x;
			this.y = bounds.y;
			this.width = bounds.width;
			this.height = bounds.height;
			this.shape = shape;
		}

		private boolean matches(boolean resized, double scale, Rectangle bounds)
		{
			return this.resized == resized
				&& Double.compare(this.scale, scale) == 0
				&& this.x == bounds.x
				&& this.y == bounds.y
				&& this.width == bounds.width
				&& this.height == bounds.height;
		}
	}

	/** 缓存的小地图可点击区域。改窗口大小会自动失效（控件矩形变了）。 */
	private static volatile ClipAreaCache clipAreaCache;

	/**
	 * Retrieves the minimap clipping area as a polygon derived from the minimap alpha mask sprite,
	 * and scales it inward to avoid overlapping edge elements.
	 *
	 * <p><b>缓存说明</b>：直接算多边形要把 alpha 蒙版逐像素扫一遍（每格几万像素），
	 * 还要往客户端线程取一次贴图。原版在选点时会用「能不能点小地图」当谓词逐个试候选点，
	 * 一次选点能触发十几次，于是每点一次小地图都要付十几遍这个代价——这是跑路发顿的主因之一。
	 * 这里按（缩放方式、缩放系数、控件矩形）缓存多边形，正常跑动时只会算一次。</p>
	 *
	 * @param scale The scale factor to shrink the polygon (e.g., 0.94 for 94% of the original size).
	 * @return A {@link Shape} representing the scaled minimap clickable area, or a fallback shape if the sprite is unavailable.
	 */
	public static Shape getMinimapClipArea(double scale) {
		Widget minimapWidget = getMinimapDrawWidget();
		if (minimapWidget == null) {
			return null;
		}
		Rectangle bounds = minimapWidget.getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
			return null;
		}
		boolean resized = Microbot.getClient().isResized();
		ClipAreaCache cached = clipAreaCache;
		if (cached != null && cached.matches(resized, scale, bounds)) {
			return cached.shape;
		}
		Shape shape = buildMinimapClipArea(resized, scale, bounds);
		if (shape != null) {
			clipAreaCache = new ClipAreaCache(resized, scale, bounds, shape);
		}
		return shape;
	}

	/** 真正构建多边形（只在缓存失效时调用）。 */
	private static Shape buildMinimapClipArea(boolean resized, double scale, Rectangle bounds) {
		BufferedImage minimapSprite = Microbot.getClientThread().runOnClientThreadOptional(() ->
			Microbot.getSpriteManager().getSprite(
				resized ? SpriteID.RESIZEABLE_MODE_MINIMAP_ALPHA_MASK : SpriteID.FIXED_MODE_MINIMAP_ALPHA_MASK, 0)).orElse(null);

		if (minimapSprite == null) {
			// 蒙版贴图取不到时退回外接椭圆（和旧的 simple 路径一致）。
			return getMinimapClipAreaSimple();
		}

		Shape rawClipArea = bufferedImageToPolygon(minimapSprite, bounds);
		return shrinkShape(rawClipArea, scale);
	}

	/**
	 * Retrieves the minimap clipping area as a {@link Shape}, scaled to slightly reduce its size.
	 * <p>
	 * This is useful for rendering overlays within the minimap without overlapping UI elements such as the globe icon.
	 *
	 * @return A {@link Shape} representing the scaled minimap clip area, or {@code null} if the minimap widget is unavailable.
	 */
	public static Shape getMinimapClipArea() {
		return getMinimapClipArea(Microbot.getClient().isResized() ? 0.94 : 1.0);
	}

    /**
     * Converts a BufferedImage to a polygon by detecting the border based on the outside color.
     *
     * @param image         The image to convert.
     * @param minimapBounds The bounds of the minimap widget.
     * @return A polygon representing the minimap's clickable area.
     */
    private static Polygon bufferedImageToPolygon(BufferedImage image, Rectangle minimapBounds) {
        Color outsideColour = null;
        Color previousColour;
        final int width = image.getWidth();
        final int height = image.getHeight();
        List<java.awt.Point> points = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            previousColour = outsideColour;
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int a = (rgb & 0xff000000) >>> 24;
                int r = (rgb & 0x00ff0000) >> 16;
                int g = (rgb & 0x0000ff00) >> 8;
                int b = (rgb & 0x000000ff);
                Color colour = new Color(r, g, b, a);
                if (x == 0 && y == 0) {
                    outsideColour = colour;
                    previousColour = colour;
                }
                if (!colour.equals(outsideColour) && previousColour.equals(outsideColour)) {
                    points.add(new java.awt.Point(x, y));
                }
                if ((colour.equals(outsideColour) || x == (width - 1)) && !previousColour.equals(outsideColour)) {
                    points.add(0, new java.awt.Point(x, y));
                }
                previousColour = colour;
            }
        }

        int offsetX = minimapBounds.x;
        int offsetY = minimapBounds.y;
        Polygon polygon = new Polygon();
        for (java.awt.Point point : points) {
            polygon.addPoint(point.x + offsetX, point.y + offsetY);
        }
        return polygon;
    }

	/**
	 * Shrinks the given shape toward its center by the specified scale factor.
	 *
	 * @param shape The original shape to shrink.
	 * @param scale The scale factor (e.g., 0.94 = 94% size). Must be > 0 and < 1 to reduce the shape.
	 * @return A new {@link Shape} that is scaled inward toward its center.
	 */
	private static Shape shrinkShape(Shape shape, double scale) {
		Rectangle bounds = shape.getBounds();
		double centerX = bounds.getCenterX();
		double centerY = bounds.getCenterY();

		AffineTransform shrink = AffineTransform.getTranslateInstance(centerX, centerY);
		shrink.scale(scale, scale);
		shrink.translate(-centerX, -centerY);

		return shrink.createTransformedShape(shape);
	}

    /**
     * Checks if a given point is inside the minimap clipping area.
     *
     * @param point The point to check.
     * @return {@code true} if the point is within the minimap bounds, {@code false} otherwise.
     */
    public static boolean isPointInsideMinimap(Point point) {
        Shape minimapClipArea = getMinimapClipArea();
        return minimapClipArea != null && minimapClipArea.contains(point.getX(), point.getY());
    }
}