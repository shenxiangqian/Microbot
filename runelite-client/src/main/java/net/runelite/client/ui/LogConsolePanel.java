/*
 * Copyright (c) 2024 Microbot Contributors
 * All rights reserved.
 */
package net.runelite.client.ui;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.MatteBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import net.runelite.api.Constants;

final class LogConsolePanel extends JPanel
{
	private static final int MAX_CHARACTERS = 100_000;
	private static final int MAX_RAW_CHARACTERS = 150_000;
	private static final int PREFERRED_HEIGHT = 160;
	private static final Color ERROR_COLOR = new Color(255, 90, 90);
	private static final Color WARNING_COLOR = new Color(255, 210, 70);
	private static final Color DEBUG_COLOR = new Color(0, 255, 70);
	private static final Pattern LOG_LINE_PATTERN = Pattern.compile(
		"^(\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2}:\\d{2}) (\\S+) \\[([^]]+)] "
			+ "(TRACE|DEBUG|INFO|WARN|ERROR)\\s+(.+?) - (.*)$");

	private final StringBuilder rawText = new StringBuilder();
	private final JTextPane textPane = new JTextPane();
	private final JCheckBox showDate = new JCheckBox("Date");
	private final JCheckBox showThread = new JCheckBox("Thread");
	private final JCheckBox showLogger = new JCheckBox("Class/logger");
	private final Style defaultStyle;
	private final Style errorStyle;
	private final Style warningStyle;
	private final Style infoStyle;
	private final Style debugStyle;

	LogConsolePanel()
	{
		super(new BorderLayout());
		setPreferredSize(new Dimension(Constants.GAME_FIXED_SIZE.width, PREFERRED_HEIGHT));
		setBorder(new MatteBorder(1, 0, 0, 0, ColorScheme.DARKER_GRAY_COLOR));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JPanel displayOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		displayOptions.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		configureOption(showDate, "Include the date and timezone");
		configureOption(showThread, "Include the thread name");
		configureOption(showLogger, "Include the logger, usually the class name");
		displayOptions.add(showDate);
		displayOptions.add(showThread);
		displayOptions.add(showLogger);
		add(displayOptions, BorderLayout.NORTH);

		textPane.setEditable(false);
		textPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		textPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		textPane.setForeground(Color.WHITE);
		textPane.setBorder(null);

		defaultStyle = createStyle("default", ColorScheme.LIGHT_GRAY_COLOR);
		errorStyle = createStyle("error", ERROR_COLOR);
		warningStyle = createStyle("warning", WARNING_COLOR);
		infoStyle = createStyle("info", Color.WHITE);
		debugStyle = createStyle("debug", DEBUG_COLOR);

		DefaultCaret caret = (DefaultCaret) textPane.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		JScrollPane scrollPane = new JScrollPane(textPane);
		scrollPane.setBorder(null);
		add(scrollPane, BorderLayout.CENTER);
	}

	private void configureOption(JCheckBox option, String tooltip)
	{
		option.setOpaque(false);
		option.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		option.setToolTipText(tooltip);
		option.addActionListener(event -> rebuildFromRawText());
	}

	private Style createStyle(String name, Color color)
	{
		Style style = textPane.addStyle(name, null);
		StyleConstants.setForeground(style, color);
		StyleConstants.setFontFamily(style, Font.MONOSPACED);
		StyleConstants.setFontSize(style, 12);
		return style;
	}

	void append(String text)
	{
		if (text == null || text.isEmpty())
		{
			return;
		}

		String sanitized = text.replace("\r", "");
		if (SwingUtilities.isEventDispatchThread())
		{
			appendOnEdt(sanitized);
		}
		else
		{
			SwingUtilities.invokeLater(() -> appendOnEdt(sanitized));
		}
	}

	void clear()
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			clearOnEdt();
		}
		else
		{
			SwingUtilities.invokeLater(this::clearOnEdt);
		}
	}

	OutputStream createOutputStream()
	{
		return new ConsoleOutputStream();
	}

	private void appendOnEdt(String text)
	{
		rawText.append(text);
		trimRawTextIfNecessary();
		appendFormattedText(text);
		trimIfNecessary();
		textPane.setCaretPosition(textPane.getDocument().getLength());
	}

	private void clearOnEdt()
	{
		rawText.setLength(0);
		textPane.setText("");
	}

	private void rebuildFromRawText()
	{
		textPane.setText("");
		appendFormattedText(rawText.toString());
		trimIfNecessary();
		textPane.setCaretPosition(textPane.getDocument().getLength());
	}

	private void appendFormattedText(String text)
	{
		StyledDocument document = textPane.getStyledDocument();
		int lineStart = 0;
		try
		{
			while (lineStart < text.length())
			{
				int newline = text.indexOf('\n', lineStart);
				int lineEnd = newline >= 0 ? newline : text.length();
				String line = text.substring(lineStart, lineEnd);
				String formatted = formatLogLine(
					line, showDate.isSelected(), showThread.isSelected(), showLogger.isSelected());
				if (newline >= 0)
				{
					formatted += '\n';
				}
				document.insertString(document.getLength(), formatted, styleForLine(line));
				if (newline < 0)
				{
					break;
				}
				lineStart = newline + 1;
			}
		}
		catch (BadLocationException ex)
		{
			// Ignore append failures to avoid recursive logging.
		}
	}

	private Style styleForLine(String line)
	{
		String level = getLogLevel(line);
		if (level == null)
		{
			return defaultStyle;
		}
		switch (level)
		{
			case "ERROR":
				return errorStyle;
			case "WARN":
				return warningStyle;
			case "INFO":
				return infoStyle;
			case "DEBUG":
			case "TRACE":
				return debugStyle;
			default:
				return defaultStyle;
		}
	}

	private void trimRawTextIfNecessary()
	{
		int excess = rawText.length() - MAX_RAW_CHARACTERS;
		if (excess <= 0)
		{
			return;
		}

		int nextLine = rawText.indexOf("\n", excess);
		rawText.delete(0, nextLine >= 0 ? nextLine + 1 : excess);
	}

	static String formatText(String text, boolean showDate, boolean showThread, boolean showLogger)
	{
		StringBuilder formatted = new StringBuilder(text.length());
		int lineStart = 0;
		while (lineStart < text.length())
		{
			int newline = text.indexOf('\n', lineStart);
			int lineEnd = newline >= 0 ? newline : text.length();
			formatted.append(formatLogLine(
				text.substring(lineStart, lineEnd), showDate, showThread, showLogger));
			if (newline < 0)
			{
				break;
			}
			formatted.append('\n');
			lineStart = newline + 1;
		}
		return formatted.toString();
	}

	private static String formatLogLine(
		String line, boolean showDate, boolean showThread, boolean showLogger)
	{
		Matcher matcher = LOG_LINE_PATTERN.matcher(line);
		if (!matcher.matches())
		{
			return line;
		}

		StringBuilder formatted = new StringBuilder(line.length());
		if (showDate)
		{
			formatted.append(matcher.group(1)).append(' ');
		}
		formatted.append(matcher.group(2));
		if (showDate)
		{
			formatted.append(' ').append(matcher.group(3));
		}
		formatted.append(' ');
		if (showThread)
		{
			formatted.append('[').append(matcher.group(4)).append("] ");
		}

		String level = matcher.group(5);
		formatted.append(level);
		for (int index = level.length(); index < 5; index++)
		{
			formatted.append(' ');
		}
		formatted.append(' ');
		if (showLogger)
		{
			formatted.append(matcher.group(6)).append(" - ");
		}
		formatted.append(matcher.group(7));
		return formatted.toString();
	}

	static String getLogLevel(String line)
	{
		Matcher matcher = LOG_LINE_PATTERN.matcher(line);
		return matcher.matches() ? matcher.group(5) : null;
	}

	private void trimIfNecessary()
	{
		Document document = textPane.getDocument();
		int excess = document.getLength() - MAX_CHARACTERS;
		if (excess <= 0)
		{
			return;
		}

		try
		{
			document.remove(0, excess);
		}
		catch (BadLocationException ex)
		{
			// Ignore trim failures to avoid recursive logging.
		}
	}

	private final class ConsoleOutputStream extends OutputStream
	{
		private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		@Override
		public synchronized void write(int b) throws IOException
		{
			if (b == '\r')
			{
				return;
			}

			buffer.write(b);
			if (b == '\n')
			{
				flushBuffer();
			}
		}

		@Override
		public synchronized void write(byte[] b, int off, int len) throws IOException
		{
			for (int i = 0; i < len; i++)
			{
				write(b[off + i]);
			}
		}

		@Override
		public synchronized void flush() throws IOException
		{
			flushBuffer();
		}

		@Override
		public void close() throws IOException
		{
			flush();
		}

		private void flushBuffer() throws IOException
		{
			if (buffer.size() == 0)
			{
				return;
			}

			String value = buffer.toString(StandardCharsets.UTF_8);
			buffer.reset();
			append(value);
		}
	}
}
