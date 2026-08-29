package net.runelite.client.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LogConsolePanelTest
{
	private static final String LOG_LINE = "2026-08-29 10:33:49 CST [AWT-EventQueue-0] "
		+ "INFO  c.s.test2.StandaloneExamplePlugin - StandaloneExamplePlugin starting up444444\n";

	@Test
	public void defaultsToTimeLevelAndMessage()
	{
		assertEquals(
			"10:33:49 INFO  StandaloneExamplePlugin starting up444444\n",
			LogConsolePanel.formatText(LOG_LINE, false, false, false));
	}

	@Test
	public void includesSelectedDiagnosticFields()
	{
		assertEquals(
			LOG_LINE,
			LogConsolePanel.formatText(LOG_LINE, true, true, true));
	}

	@Test
	public void preservesPlainOutputAndStackTraceLines()
	{
		String output = "Plain system output\n\tat example.Script.run(Script.java:42)\n";
		assertEquals(output, LogConsolePanel.formatText(output, false, false, false));
	}

	@Test
	public void identifiesLogLevelsForColoring()
	{
		assertEquals("INFO", LogConsolePanel.getLogLevel(LOG_LINE.trim()));
		assertEquals("ERROR", LogConsolePanel.getLogLevel(LOG_LINE.replace("INFO ", "ERROR").trim()));
		assertEquals("WARN", LogConsolePanel.getLogLevel(LOG_LINE.replace("INFO ", "WARN ").trim()));
		assertEquals("DEBUG", LogConsolePanel.getLogLevel(LOG_LINE.replace("INFO ", "DEBUG").trim()));
		assertNull(LogConsolePanel.getLogLevel("Plain system output"));
	}
}
