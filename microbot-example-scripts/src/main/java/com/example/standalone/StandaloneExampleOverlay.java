package com.example.standalone;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.Dimension;

/**
 * Minimal overlay that confirms the side-loaded plugin got its injected
 * {@link Client} reference. If the overlay shows up in the client's
 * "Overlays" panel, dependency injection is wired correctly.
 *
 * <p>Returning {@code null} from {@link #render} means we never paint anything;
 * we only need an instance so {@link OverlayManager} keeps us alive.
 */
public class StandaloneExampleOverlay extends Overlay {

    @Inject
    private Client client;

    @Inject
    public StandaloneExampleOverlay() {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(java.awt.Graphics2D graphics) {
        if (!Microbot.isLoggedIn() || client == null) {
            return null;
        }
        // No-op render — this overlay only exists to prove injection works.
        return null;
    }
}