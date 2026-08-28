package net.runelite.client.plugins.microbot.ui;

import org.junit.Test;

import static net.runelite.client.plugins.microbot.ui.ScriptToolbarController.ToolbarState.BUSY;
import static net.runelite.client.plugins.microbot.ui.ScriptToolbarController.ToolbarState.IDLE;
import static net.runelite.client.plugins.microbot.ui.ScriptToolbarController.ToolbarState.IDLE_WITH_RESTART;
import static net.runelite.client.plugins.microbot.ui.ScriptToolbarController.ToolbarState.MULTIPLE_RUNNING;
import static net.runelite.client.plugins.microbot.ui.ScriptToolbarController.ToolbarState.RUNNING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScriptToolbarControllerStateTest {
    @Test
    public void keepsRestartAvailableAfterLastScriptStops() {
        assertEquals(IDLE_WITH_RESTART,
                ScriptToolbarController.determineToolbarState(false, 0, true));
    }

    @Test
    public void hidesRestartUntilAScriptHasStarted() {
        assertEquals(IDLE,
                ScriptToolbarController.determineToolbarState(false, 0, false));
    }

    @Test
    public void runningAndBusyStatesTakePrecedence() {
        assertEquals(RUNNING,
                ScriptToolbarController.determineToolbarState(false, 1, true));
        assertEquals(MULTIPLE_RUNNING,
                ScriptToolbarController.determineToolbarState(false, 2, true));
        assertEquals(BUSY,
                ScriptToolbarController.determineToolbarState(true, 0, true));
    }

    @Test
    public void disablesInputWhileAnExternalScriptIsRunning() {
        assertTrue(ScriptToolbarController.determineInputEnabled(0));
        assertFalse(ScriptToolbarController.determineInputEnabled(1));
        assertFalse(ScriptToolbarController.determineInputEnabled(2));
    }
}
