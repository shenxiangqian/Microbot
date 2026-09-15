package net.runelite.client.plugins.microbot.questhelper.requirements.zone;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;

public class PolyZone extends Zone
{
	List<Point2D.Double> perimeter = new ArrayList<>();

	private int minPlane = 8;
	private int maxPlane = 0;

	int minX = Integer.MAX_VALUE;
	int minY = Integer.MAX_VALUE;

	//The first plane of the "Overworld"
	public PolyZone(List<WorldPoint> permiter)
	{
		// Check minimum z, check for max z
		for (WorldPoint p : permiter)
		{
			Point2D.Double pos = new Point2D.Double();
			pos.x = p.getX();
			pos.y = p.getY();
			this.perimeter.add(pos);

			if (p.getPlane() > maxPlane)
			{
				maxPlane = p.getPlane();
			}
			if (p.getPlane() < minPlane)
			{
				minPlane = p.getPlane();
			}

			if (p.getX() < minX)
			{
				minX = p.getX();
			}
			if (p.getY() < minY)
			{
				minY = p.getY();
			}
		}
	}

	public PolyZone(Rs2WorldArea area){
		List<WorldPoint> corners = new ArrayList<>();

		int x = area.getX();
		int y = area.getY();
		int width = area.getWidth();
		int height = area.getHeight();
		int plane = area.getPlane();

		corners.add(new WorldPoint(x,y,plane));
		corners.add(new WorldPoint(x+width,y,plane));
		corners.add(new WorldPoint(x+width,y+height,plane));
		corners.add(new WorldPoint(x,y+height,plane));

		for (WorldPoint p : corners)
		{
			Point2D.Double pos = new Point2D.Double();
			pos.x = p.getX();
			pos.y = p.getY();
			this.perimeter.add(pos);

			if (p.getPlane() > maxPlane)
			{
				maxPlane = p.getPlane();
			}
			if (p.getPlane() < minPlane)
			{
				minPlane = p.getPlane();
			}

			if (p.getX() < minX)
			{
				minX = p.getX();
			}
			if (p.getY() < minY)
			{
				minY = p.getY();
			}
		}
	}

	public boolean contains(WorldPoint worldPoint)
	{
		if(getCenter().getPlane() != worldPoint.getPlane()) return false;
		Path2D.Double path = new Path2D.Double();
		Point2D.Double firstVertex = perimeter.get(0);
		path.moveTo(firstVertex.x, firstVertex.y);

		for (int i = 1; i < perimeter.size(); i++)
		{
			Point2D.Double vertex = perimeter.get(i);
			path.lineTo(vertex.x, vertex.y);
		}

		path.closePath();
		return path.contains(worldPoint.getX(), worldPoint.getY());
	}

	public WorldPoint getMinWorldPoint()
	{

		return new WorldPoint(minX, minY, minPlane);
	}

	/**
	 * Returns the geometric centroid of this polygon using the Shoelace formula.
	 * Coordinates are rounded to the nearest integer tile.
	 */
	public WorldPoint getCenter()
	{
		double sumX = 0, sumY = 0;
		double signedArea = 0;
		int n = perimeter.size();

		for (int i = 0; i < n; i++)
		{
			Point2D.Double p0 = perimeter.get(i);
			Point2D.Double p1 = perimeter.get((i + 1) % n);
			double cross = p0.x * p1.y - p1.x * p0.y;
			signedArea += cross;
			sumX += (p0.x + p1.x) * cross;
			sumY += (p0.y + p1.y) * cross;
		}

		signedArea *= 0.5;
		if (Math.abs(signedArea) < 1e-9)
		{
			// Degenerate polygon (collinear points) — fall back to bounding-box center.
			// Requires extending PolyZone to also track maxX/maxY, or scan perimeter once.
			return new WorldPoint(minX, minY, minPlane);
		}

		sumX /= (6 * signedArea);
		sumY /= (6 * signedArea);

		return new WorldPoint((int) Math.round(sumX), (int) Math.round(sumY), minPlane);
	}
}
