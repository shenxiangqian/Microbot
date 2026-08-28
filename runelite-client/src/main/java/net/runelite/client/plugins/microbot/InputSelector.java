package net.runelite.client.plugins.microbot;

import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.image.BufferedImage;

@Singleton
public class InputSelector {

    private static final BufferedImage ENABLED_IMAGE, DISABLED_IMAGE;

    static {
        ENABLED_IMAGE = ImageUtil.loadImageResource(Microbot.class, "enabled_small.png");
        DISABLED_IMAGE = ImageUtil.loadImageResource(Microbot.class, "disabled_small.png");
    }

    private final ClientToolbar clientToolbar;
    private final NavigationButton enableButton;
    private final NavigationButton disableButton;
    private boolean started;

    @Inject
    public InputSelector(ClientToolbar clientToolbar) {
        this.clientToolbar = clientToolbar;
        enableButton = NavigationButton.builder()
                .tab(false)
                .icon(ENABLED_IMAGE)
                .tooltip("Enable Input")
                .onClick(this::enableClick)
                .build();
        disableButton = NavigationButton.builder()
                .tab(false)
                .icon(DISABLED_IMAGE)
                .tooltip("Disable Input")
                .onClick(this::disableClick)
                .build();
    }

    public void startUp() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::startUp);
            return;
        }
        if (started) {
            return;
        }
        started = true;
        addAndRemoveButtons();
    }

    public void shutDown() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::shutDown);
            return;
        }
        if (!started) {
            return;
        }
        started = false;
        clientToolbar.removeNavigation(enableButton);
        clientToolbar.removeNavigation(disableButton);
    }

    private void addAndRemoveButtons() {
        if (!started) {
            return;
        }
        clientToolbar.removeNavigation(enableButton);
        clientToolbar.removeNavigation(disableButton);
        Component client = ClientUI.getClient();
        clientToolbar.addNavigation(client == null || !client.isEnabled() ? enableButton : disableButton);
    }

    public void enableClick() {
        setInputEnabled(true);
    }

    public void disableClick() {
        setInputEnabled(false);
    }

    public void setInputEnabled(boolean enabled) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setInputEnabled(enabled));
            return;
        }
        Component client = ClientUI.getClient();
        if (client != null) {
            client.setEnabled(enabled);
        }
        if (Microbot.getClient() != null && Microbot.getClient().getCanvas() != null) {
            Microbot.getClient().getCanvas().setFocusable(enabled);
        }
        addAndRemoveButtons();
    }
}
