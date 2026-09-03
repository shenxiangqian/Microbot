package net.runelite.client.plugins.microbot;

import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

@Singleton
public class InputSelector {

    private static final BufferedImage ENABLED_IMAGE, DISABLED_IMAGE;

    static {
        ENABLED_IMAGE = ImageUtil.loadImageResource(Microbot.class, "enabled_small.png");
        DISABLED_IMAGE = ImageUtil.loadImageResource(Microbot.class, "disabled_small.png");
    }

    private final ClientToolbar clientToolbar;
    private final MouseManager mouseManager;
    private final KeyManager keyManager;
    private final NavigationButton enableButton;
    private final NavigationButton disableButton;
    private final MouseListener mouseBlocker = new MouseAdapter() {
        @Override
        public MouseEvent mouseClicked(MouseEvent mouseEvent) {
            consumeIfBlocked(mouseEvent);
            return mouseEvent;
        }

        @Override
        public MouseEvent mousePressed(MouseEvent mouseEvent) {
            consumeIfBlocked(mouseEvent);
            return mouseEvent;
        }

        @Override
        public MouseEvent mouseDragged(MouseEvent mouseEvent) {
            consumeIfBlocked(mouseEvent);
            return mouseEvent;
        }

        @Override
        public MouseEvent mouseMoved(MouseEvent mouseEvent) {
            consumeIfBlocked(mouseEvent);
            return mouseEvent;
        }

        @Override
        public MouseEvent mouseEntered(MouseEvent mouseEvent) {
            consumeIfBlocked(mouseEvent);
            return mouseEvent;
        }

        @Override
        public MouseEvent mouseExited(MouseEvent mouseEvent) {
            consumeIfBlocked(mouseEvent);
            return mouseEvent;
        }
    };
    private final MouseWheelListener mouseWheelBlocker = mouseWheelEvent -> {
        consumeIfBlocked(mouseWheelEvent);
        return mouseWheelEvent;
    };
    private final KeyListener keyBlocker = new KeyListener() {
        @Override
        public void keyTyped(KeyEvent keyEvent) {
            consumeIfBlocked(keyEvent);
        }

        @Override
        public void keyPressed(KeyEvent keyEvent) {
            consumeIfBlocked(keyEvent);
        }

        @Override
        public void keyReleased(KeyEvent keyEvent) {
            // Releases must reach the client so input held when blocking began cannot remain stuck.
        }

        @Override
        public boolean isEnabledOnLoginScreen() {
            return true;
        }
    };
    private volatile boolean inputEnabled = true;
    private boolean started;

    @Inject
    public InputSelector(ClientToolbar clientToolbar, MouseManager mouseManager, KeyManager keyManager) {
        this.clientToolbar = clientToolbar;
        this.mouseManager = mouseManager;
        this.keyManager = keyManager;
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
        mouseManager.registerMouseListener(0, mouseBlocker);
        mouseManager.registerMouseWheelListener(0, mouseWheelBlocker);
        keyManager.registerKeyListener(0, keyBlocker);
        applyClientInputState();
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
        inputEnabled = true;
        mouseManager.unregisterMouseListener(mouseBlocker);
        mouseManager.unregisterMouseWheelListener(mouseWheelBlocker);
        keyManager.unregisterKeyListener(keyBlocker);
        applyClientInputState();
        clientToolbar.removeNavigation(enableButton);
        clientToolbar.removeNavigation(disableButton);
    }

    private void addAndRemoveButtons() {
        if (!started) {
            return;
        }
        clientToolbar.removeNavigation(enableButton);
        clientToolbar.removeNavigation(disableButton);
        clientToolbar.addNavigation(inputEnabled ? disableButton : enableButton);
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
        inputEnabled = enabled;
        applyClientInputState();
        addAndRemoveButtons();
    }

    private void applyClientInputState() {
        Component client = ClientUI.getClient();
        Canvas canvas = Microbot.getClient() == null ? null : Microbot.getClient().getCanvas();
        applyClientInputState(client, canvas, inputEnabled);
    }

    static void applyClientInputState(Component client, Canvas canvas, boolean enabled) {
        if (client != null) {
            client.setEnabled(enabled);
        }
        if (canvas != null) {
            canvas.setFocusable(enabled);
        }
    }

    private void consumeIfBlocked(InputEvent event) {
        if (!inputEnabled && !BotEventGuard.isSynthetic()) {
            event.consume();
        }
    }
}
