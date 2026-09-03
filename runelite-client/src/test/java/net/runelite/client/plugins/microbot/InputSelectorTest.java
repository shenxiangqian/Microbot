package net.runelite.client.plugins.microbot;

import java.awt.Canvas;
import java.awt.Panel;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.SwingUtilities;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.ui.ClientToolbar;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class InputSelectorTest
{
	private final ClientToolbar clientToolbar = mock(ClientToolbar.class);
	private final MouseManager mouseManager = mock(MouseManager.class);
	private final KeyManager keyManager = mock(KeyManager.class);
	private InputSelector inputSelector;
	private MouseListener mouseBlocker;
	private MouseWheelListener mouseWheelBlocker;
	private KeyListener keyBlocker;

	@Before
	public void setUp() throws Exception
	{
		inputSelector = new InputSelector(clientToolbar, mouseManager, keyManager);
		onEdt(inputSelector::startUp);

		ArgumentCaptor<MouseListener> mouseCaptor = ArgumentCaptor.forClass(MouseListener.class);
		ArgumentCaptor<MouseWheelListener> wheelCaptor = ArgumentCaptor.forClass(MouseWheelListener.class);
		ArgumentCaptor<KeyListener> keyCaptor = ArgumentCaptor.forClass(KeyListener.class);
		verify(mouseManager).registerMouseListener(eq(0), mouseCaptor.capture());
		verify(mouseManager).registerMouseWheelListener(eq(0), wheelCaptor.capture());
		verify(keyManager).registerKeyListener(eq(0), keyCaptor.capture());
		mouseBlocker = mouseCaptor.getValue();
		mouseWheelBlocker = wheelCaptor.getValue();
		keyBlocker = keyCaptor.getValue();
	}

	@After
	public void tearDown() throws Exception
	{
		while (BotEventGuard.isSynthetic())
		{
			BotEventGuard.end();
		}
		onEdt(inputSelector::shutDown);
	}

	@Test
	public void disabledInputBlocksRealMouseMovement() throws Exception
	{
		onEdt(() -> inputSelector.setInputEnabled(false));
		MouseEvent moved = mouseEvent(MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON);
		MouseEvent entered = mouseEvent(MouseEvent.MOUSE_ENTERED, MouseEvent.NOBUTTON);
		MouseEvent exited = mouseEvent(MouseEvent.MOUSE_EXITED, MouseEvent.NOBUTTON);

		mouseBlocker.mouseMoved(moved);
		mouseBlocker.mouseEntered(entered);
		mouseBlocker.mouseExited(exited);

		assertTrue(moved.isConsumed());
		assertTrue(entered.isConsumed());
		assertTrue(exited.isConsumed());
	}

	@Test
	public void inputSwitchControlsClientAndCanvasState()
	{
		Panel client = new Panel();
		Canvas canvas = new Canvas();

		InputSelector.applyClientInputState(client, canvas, false);

		assertFalse(client.isEnabled());
		assertFalse(canvas.isFocusable());

		InputSelector.applyClientInputState(client, canvas, true);

		assertTrue(client.isEnabled());
		assertTrue(canvas.isFocusable());
	}

	@Test
	public void disabledInputBlocksRealActions() throws Exception
	{
		onEdt(() -> inputSelector.setInputEnabled(false));
		MouseEvent pressed = mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1);
		MouseEvent dragged = mouseEvent(MouseEvent.MOUSE_DRAGGED, MouseEvent.BUTTON1);
		MouseWheelEvent wheel = mouseWheelEvent();
		KeyEvent key = keyEvent();

		mouseBlocker.mousePressed(pressed);
		mouseBlocker.mouseDragged(dragged);
		mouseWheelBlocker.mouseWheelMoved(wheel);
		keyBlocker.keyPressed(key);

		assertTrue(pressed.isConsumed());
		assertTrue(dragged.isConsumed());
		assertTrue(wheel.isConsumed());
		assertTrue(key.isConsumed());
	}

	@Test
	public void disabledInputAllowsReleaseEventsToClearHeldState() throws Exception
	{
		onEdt(() -> inputSelector.setInputEnabled(false));
		MouseEvent released = mouseEvent(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1);
		KeyEvent keyReleased = new KeyEvent(new Canvas(), KeyEvent.KEY_RELEASED, 0L, 0, KeyEvent.VK_A, 'a');

		mouseBlocker.mouseReleased(released);
		keyBlocker.keyReleased(keyReleased);

		assertFalse(released.isConsumed());
		assertFalse(keyReleased.isConsumed());
	}

	@Test
	public void disabledInputAllowsSyntheticActions() throws Exception
	{
		onEdt(() -> inputSelector.setInputEnabled(false));
		MouseEvent moved = mouseEvent(MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON);
		MouseEvent pressed = mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1);
		MouseWheelEvent wheel = mouseWheelEvent();
		KeyEvent key = keyEvent();

		BotEventGuard.begin();
		try
		{
			mouseBlocker.mouseMoved(moved);
			mouseBlocker.mousePressed(pressed);
			mouseWheelBlocker.mouseWheelMoved(wheel);
			keyBlocker.keyPressed(key);
		}
		finally
		{
			BotEventGuard.end();
		}

		assertFalse(moved.isConsumed());
		assertFalse(pressed.isConsumed());
		assertFalse(wheel.isConsumed());
		assertFalse(key.isConsumed());
	}

	@Test
	public void enabledInputAllowsRealActions()
	{
		MouseEvent pressed = mouseEvent(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1);
		KeyEvent key = keyEvent();

		mouseBlocker.mousePressed(pressed);
		keyBlocker.keyPressed(key);

		assertFalse(pressed.isConsumed());
		assertFalse(key.isConsumed());
	}

	private static MouseEvent mouseEvent(int id, int button)
	{
		return new MouseEvent(new Canvas(), id, 0L, 0, 10, 20, 1, false, button);
	}

	private static MouseWheelEvent mouseWheelEvent()
	{
		return new MouseWheelEvent(new Canvas(), MouseEvent.MOUSE_WHEEL, 0L, 0, 10, 20, 0, false,
			MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1);
	}

	private static KeyEvent keyEvent()
	{
		return new KeyEvent(new Canvas(), KeyEvent.KEY_PRESSED, 0L, 0, KeyEvent.VK_A, 'a');
	}

	private static void onEdt(Runnable action) throws Exception
	{
		SwingUtilities.invokeAndWait(action);
	}
}
