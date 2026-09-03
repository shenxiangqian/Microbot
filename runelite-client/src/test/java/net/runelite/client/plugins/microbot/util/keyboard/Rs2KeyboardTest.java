package net.runelite.client.plugins.microbot.util.keyboard;

import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import org.junit.After;
import org.junit.Test;

import java.awt.Canvas;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the VK → character mapping that {@link Rs2Keyboard#keyPress(int)} uses to
 * decide whether to emit an accompanying {@code KEY_TYPED} event. Without the
 * {@code KEY_TYPED}, OSRS dialog option widgets whose {@code onKeyListener} reads
 * the character (e.g. pressing "1" to pick the first option) silently ignore the
 * press — which is the regression this mapping exists to fix.
 *
 * The full dispatch path is not exercised here because it requires a realized AWT
 * Canvas in a visible Frame to deliver events through the focus manager. That is
 * covered by manual QA and integration runs; the unit guarantee is that every
 * printable key the automation uses produces the expected char, and every
 * non-printable key produces {@link KeyEvent#CHAR_UNDEFINED}.
 */
public class Rs2KeyboardTest {
	@After
	public void clearGuardState() {
		while (BotEventGuard.isSynthetic()) {
			BotEventGuard.end();
		}
	}

	@Test
	public void dispatchMarksEventsAsSynthetic() {
		Canvas canvas = new Canvas();
		AtomicBoolean syntheticDuringDispatch = new AtomicBoolean();
		canvas.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent event) {
				syntheticDuringDispatch.set(BotEventGuard.isSynthetic());
			}
		});

		Rs2Keyboard.dispatchKeyEvent(canvas, KeyEvent.KEY_PRESSED, KeyEvent.VK_A, 'a', 0);

		assertTrue(syntheticDuringDispatch.get());
		assertFalse(BotEventGuard.isSynthetic());
	}

	@Test
	public void digitsMapToDigitChars() {
		assertEquals('0', Rs2Keyboard.toTypedChar(KeyEvent.VK_0));
		assertEquals('1', Rs2Keyboard.toTypedChar(KeyEvent.VK_1));
		assertEquals('5', Rs2Keyboard.toTypedChar(KeyEvent.VK_5));
		assertEquals('9', Rs2Keyboard.toTypedChar(KeyEvent.VK_9));
	}

	@Test
	public void lettersMapToLowercaseChars() {
		assertEquals('a', Rs2Keyboard.toTypedChar(KeyEvent.VK_A));
		assertEquals('m', Rs2Keyboard.toTypedChar(KeyEvent.VK_M));
		assertEquals('z', Rs2Keyboard.toTypedChar(KeyEvent.VK_Z));
	}

	@Test
	public void spaceMapsToSpace() {
		assertEquals(' ', Rs2Keyboard.toTypedChar(KeyEvent.VK_SPACE));
	}

	@Test
	public void enterMapsToNewline() {
		assertEquals('\n', Rs2Keyboard.toTypedChar(KeyEvent.VK_ENTER));
	}

	@Test
	public void tabMapsToTab() {
		assertEquals('\t', Rs2Keyboard.toTypedChar(KeyEvent.VK_TAB));
	}

	@Test
	public void backspaceMapsToBackspace() {
		assertEquals('\b', Rs2Keyboard.toTypedChar(KeyEvent.VK_BACK_SPACE));
	}

	@Test
	public void escapeMapsToEscapeChar() {
		assertEquals((char) 27, Rs2Keyboard.toTypedChar(KeyEvent.VK_ESCAPE));
	}

	@Test
	public void functionKeysAreNonPrintable() {
		for (int vk : new int[]{KeyEvent.VK_F1, KeyEvent.VK_F5, KeyEvent.VK_F12}) {
			assertEquals("VK " + vk + " must be non-printable",
					KeyEvent.CHAR_UNDEFINED, Rs2Keyboard.toTypedChar(vk));
		}
	}

	@Test
	public void modifiersAreNonPrintable() {
		for (int vk : new int[]{KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META}) {
			assertEquals(KeyEvent.CHAR_UNDEFINED, Rs2Keyboard.toTypedChar(vk));
		}
	}

	@Test
	public void arrowKeysAreNonPrintable() {
		for (int vk : new int[]{KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT}) {
			assertEquals(KeyEvent.CHAR_UNDEFINED, Rs2Keyboard.toTypedChar(vk));
		}
	}

	@Test
	public void dialogOptionSelectionKeysAllPrintable() {
		// Regression guard: OSRS dialog options are selected with VK_1 .. VK_5,
		// so all five MUST produce a KEY_TYPED-eligible char.
		for (int vk = KeyEvent.VK_1; vk <= KeyEvent.VK_5; vk++) {
			char c = Rs2Keyboard.toTypedChar(vk);
			assertEquals("dialog option key '" + (char)('0' + (vk - KeyEvent.VK_0))
					+ "' must be printable for onKeyListener to fire",
					(char)('0' + (vk - KeyEvent.VK_0)), c);
		}
	}
}
