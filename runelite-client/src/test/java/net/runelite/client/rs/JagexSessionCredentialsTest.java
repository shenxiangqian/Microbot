/*
 * Copyright (c) 2026 Microbot
 * All rights reserved.
 */
package net.runelite.client.rs;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class JagexSessionCredentialsTest
{
	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	private String originalCredentialsPath;
	private Path credentialsDirectory;

	@Before
	public void setUp()
		throws Exception
	{
		originalCredentialsPath = System.getProperty(JagexSessionCredentials.CREDENTIALS_PATH_PROPERTY);
		credentialsDirectory = temporaryFolder.newFolder("runelite").toPath();
	}

	@After
	public void tearDown()
	{
		if (originalCredentialsPath == null)
		{
			System.clearProperty(JagexSessionCredentials.CREDENTIALS_PATH_PROPERTY);
		}
		else
		{
			System.setProperty(JagexSessionCredentials.CREDENTIALS_PATH_PROPERTY, originalCredentialsPath);
		}
	}

	@Test
	public void testInstallAndRemoveCredentials() throws Exception
	{
		Path credentialsFile;
		try (JagexSessionCredentials credentials = JagexSessionCredentials.install(
			credentialsDirectory, "session-value", "123456"))
		{
			credentialsFile = credentials.getCredentialsFile();
			assertNotNull(credentialsFile);
			assertEquals(credentialsFile.getFileName().toString(),
				System.getProperty(JagexSessionCredentials.CREDENTIALS_PATH_PROPERTY));
			assertEquals(credentialsDirectory, credentialsFile.getParent());

			Properties properties = new Properties();
			try (InputStream input = Files.newInputStream(credentialsFile))
			{
				properties.load(input);
			}
			assertEquals("session-value", properties.getProperty(JagexSessionCredentials.SESSION_ID_PROPERTY));
			assertEquals("123456", properties.getProperty(JagexSessionCredentials.CHARACTER_ID_PROPERTY));
		}

		assertFalse(Files.exists(credentialsFile));
		assertEquals(originalCredentialsPath,
			System.getProperty(JagexSessionCredentials.CREDENTIALS_PATH_PROPERTY));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testRejectsPartialCredentials() throws Exception
	{
		JagexSessionCredentials.install(credentialsDirectory, "session-value", null);
	}

	@Test
	public void testNoCredentialsDoesNotChangeProperty() throws Exception
	{
		try (JagexSessionCredentials credentials = JagexSessionCredentials.install(
			credentialsDirectory, null, null))
		{
			assertNull(credentials.getCredentialsFile());
			assertEquals(originalCredentialsPath,
				System.getProperty(JagexSessionCredentials.CREDENTIALS_PATH_PROPERTY));
		}
	}
}
