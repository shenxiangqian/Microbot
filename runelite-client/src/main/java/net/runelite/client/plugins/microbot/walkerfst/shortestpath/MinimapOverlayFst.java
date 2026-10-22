package net.runelite.client.plugins.microbot.walkerfst.shortestpath;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.walkerfst.Rs2MiniMapFst;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class MinimapOverlayFst extends Overlay
{
	@Inject
	MinimapOverlayFst(ShortestPathPluginFst plugin)
	{
		super();
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
		drawAfterInterface(InterfaceID.TOPLEVEL_DISPLAY);
		setNaughty();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{

		Shape minimapClip = Rs2MiniMapFst.getMinimapClipArea();

		Widget minimapWidget = Rs2MiniMapFst.getMinimapDrawWidget();

		if (minimapWidget != null)
		{
			graphics.setColor(Color.RED);
			graphics.draw(minimapWidget.getBounds());
		}

		if (minimapClip != null)
		{
			graphics.setColor(Color.GREEN);
			graphics.draw(minimapClip);
		}

		return null;
	}
}
