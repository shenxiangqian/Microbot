package net.runelite.client.plugins.devtools;

import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

public class MicrobotMouseOverlay extends Overlay {
    private final Client client;
    private final DevToolsPlugin plugin;

    // 核心配置：16像素白色轮廓圆
    private static final int FIXED_CIRCLE_SIZE = 16;
    private static final Color COLOR_CIRCLE_STROKE = Color.WHITE;
    private static final float CIRCLE_STROKE_WIDTH = 1.5f;

    // 电风扇扇叶参数
    private static final int FAN_BLADE_COUNT = 3;
    private static final float FAN_ROTATION_SPEED = 8.0f;
    private static final Color FAN_BLADE_COLOR = new Color(255, 255, 255, 180);

    // 鼠标轨迹配置
    private static final Color COLOR_TRAIL = new Color(200, 200, 200, 120);

    private long lastUpdateTime = System.currentTimeMillis();
    private double fanRotationAngle = 0.0;

    @Inject
    MicrobotMouseOverlay(Client client, DevToolsPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);  // Use ALWAYS_ON_TOP to ensure rendering in all game states
        setPriority(Overlay.PRIORITY_LOW);
        setNaughty();
        setRequiresLoggedIn(false);  // Allow rendering in all game states (login screen, loading, etc.)
    }
    @Override
    public Dimension render(Graphics2D g) {
        if (plugin.getMouseMovement().isActive()) {
            // Safety check: ensure Microbot.getMouse() is initialized
            if (Microbot.getMouse() == null) {
                return null;
            }

            if (!Microbot.getMouse().getTimer().isRunning()) {
                Microbot.getMouse().getPoints().clear();
                Microbot.getMouse().getTimer().start();
            }

            // Enable anti-aliasing for smooth rendering
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Point mousePos = Microbot.getMouse().getLastMove();
            if (mousePos == null || (mousePos.getX() == 0 && mousePos.getY() == 0)) {
                return null;
            }

            // 1. Draw mouse trail
            drawMouseTrail(g);

            // 2. Update fan rotation angle
            updateFanRotation();

            // 3. Draw core elements: 16px white outline circle + fan blades
            drawCoreElements(g, mousePos);

        } else {
            if (Microbot.getMouse() != null) {
                Microbot.getMouse().getPoints().clear();
                Microbot.getMouse().getTimer().stop();
            }
        }

        return null;
    }

    /**
     * Draw mouse movement trail (smooth Bezier curve)
     */
    private void drawMouseTrail(Graphics2D g) {
        var points = Microbot.getMouse().getPoints();
        var pointArray = points.toArray(new Point[0]);

        if (pointArray.length < 2) {
            return;
        }

        Point firstPoint = pointArray[0];
        Point lastPoint = pointArray[pointArray.length - 1];

        // Build smooth path using quadratic Bezier curves
        Path2D path = new Path2D.Double();
        path.moveTo(firstPoint.getX(), firstPoint.getY());

        // Use midpoint-based quadTo for smooth curves
        for (int i = 1; i < pointArray.length - 2; i++) {
            Point pCurrent = pointArray[i];
            Point pNext = pointArray[i + 1];

            double midX = (pCurrent.getX() + pNext.getX()) / 2.0;
            double midY = (pCurrent.getY() + pNext.getY()) / 2.0;

            path.quadTo(pCurrent.getX(), pCurrent.getY(), midX, midY);
        }

        // Connect to the last point
        Point secondLast = pointArray[pointArray.length - 2];
        path.quadTo(secondLast.getX(), secondLast.getY(), lastPoint.getX(), lastPoint.getY());

        // Draw smooth path with rounded caps/joins
        g.setColor(COLOR_TRAIL);
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
    }

    /**
     * Update fan blade rotation angle
     */
    private void updateFanRotation() {
        long currentTime = System.currentTimeMillis();
        double elapsedSeconds = (currentTime - lastUpdateTime) / 1000.0;
        lastUpdateTime = currentTime;

        // Fan blades rotate clockwise
        fanRotationAngle += FAN_ROTATION_SPEED * 60 * elapsedSeconds;
    }

    /**
     * Draw core elements: 16px white outline circle + fan blades
     */
    private void drawCoreElements(Graphics2D g, Point mousePos) {
        int x = mousePos.getX();
        int y = mousePos.getY();
        int radius = FIXED_CIRCLE_SIZE / 2;

        // Step 1: Draw 16px white outline circle (no fill)
        g.setColor(COLOR_CIRCLE_STROKE);
        g.setStroke(new BasicStroke(CIRCLE_STROKE_WIDTH));
        g.draw(new Ellipse2D.Double(x - radius, y - radius, FIXED_CIRCLE_SIZE, FIXED_CIRCLE_SIZE));

        // Step 2: Draw fan blades (inside the outline circle)
        AffineTransform fanTransform = g.getTransform();
        g.translate(x, y);
        g.rotate(Math.toRadians(fanRotationAngle % 360));
        drawFanBlades(g, radius - 1);
        g.setTransform(fanTransform);
    }

    /**
     * Draw 3-blade fan (adapted for 16px size)
     */
    private void drawFanBlades(Graphics2D g, int bladeRadius) {
        g.setColor(FAN_BLADE_COLOR);
        g.setStroke(new BasicStroke(1.2f));

        double bladeAngleStep = 360.0 / FAN_BLADE_COUNT;
        for (int i = 0; i < FAN_BLADE_COUNT; i++) {
            AffineTransform bladeTransform = g.getTransform();
            g.rotate(Math.toRadians(i * bladeAngleStep));

            // Draw single blade
            Path2D blade = new Path2D.Double();
            blade.moveTo(0, 0);
            blade.lineTo(bladeRadius, -1);
            blade.lineTo(bladeRadius, 1);
            blade.closePath();
            g.fill(blade);
            g.draw(blade);

            g.setTransform(bladeTransform);
        }

        // Fan center small dot
        g.fill(new Ellipse2D.Double(-1, -1, 2, 2));
    }
}